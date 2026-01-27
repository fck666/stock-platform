package com.stock.platform.backend_api.service;

import com.stock.platform.backend_api.api.dto.SyncJobDto;
import com.stock.platform.backend_api.config.AppProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DataCollectorService {
    private static final int OUTPUT_TAIL_LIMIT = 4000;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private final AppProperties appProperties;
    private final Environment environment;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public DataCollectorService(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    public SyncJobDto startSyncIndices(LocalDate start, LocalDate end) {
        List<String> args = List.of(
                "main.py",
                "db-sync-indices",
                "--interval", "1d",
                "--start", start.toString(),
                "--end", end.toString()
        );
        return startJob(args);
    }

    public SyncJobDto startSyncStock(String symbol, LocalDate start, LocalDate end) {
        List<String> args = List.of(
                "main.py",
                "db-full-prices",
                "--interval", "1d",
                "--start", start.toString(),
                "--end", end.toString(),
                "--symbols", symbol,
                "--no-indices"
        );
        return startJob(args);
    }

    public SyncJobDto startSyncStocks(List<String> symbols, LocalDate start, LocalDate end) {
        String joined = String.join(",", symbols);
        List<String> args = List.of(
                "main.py",
                "db-full-prices",
                "--interval", "1d",
                "--start", start.toString(),
                "--end", end.toString(),
                "--symbols", joined,
                "--no-indices"
        );
        return startJob(args);
    }

    public SyncJobDto getJob(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return job.toDto();
    }

    private SyncJobDto startJob(List<String> scriptArgs) {
        AppProperties.DataCollector dc = appProperties.dataCollector();
        boolean enabled = dc != null && dc.enabled();
        if (!enabled) {
            throw new IllegalStateException("Data collector sync is disabled");
        }

        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId);
        jobs.put(jobId, job);

        CompletableFuture.runAsync(() -> runJob(job, scriptArgs), executor);
        return job.toDto();
    }

    private void runJob(Job job, List<String> scriptArgs) {
        job.status = "RUNNING";
        job.startedAt = Instant.now();

        try {
            AppProperties.DataCollector dc = appProperties.dataCollector();
            String workingDir = dc != null ? dc.workingDir() : null;
            if (workingDir == null || workingDir.isBlank()) {
                throw new IllegalStateException("DATA_COLLECTOR_WORKING_DIR is not configured");
            }
            String pythonPath = resolvePythonPath(workingDir);

            List<String> cmd = new ArrayList<>();
            cmd.add(pythonPath);
            cmd.addAll(scriptArgs);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(workingDir));
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            env.putIfAbsent("DB_DSN", buildDbDsnFromSpringDatasource());

            Process p = pb.start();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    job.appendOutput(line + "\n");
                }
            }

            int exit = p.waitFor();
            job.exitCode = exit;
            job.status = exit == 0 ? "SUCCEEDED" : "FAILED";
        } catch (Exception e) {
            job.appendOutput("ERROR: " + e.getMessage() + "\n");
            job.status = "FAILED";
        } finally {
            job.finishedAt = Instant.now();
        }
    }

    private String resolvePythonPath(String workingDir) {
        String configured = appProperties.dataCollector() != null ? appProperties.dataCollector().pythonPath() : null;
        if (configured != null && !configured.isBlank()) {
            return configured;
        }

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            File f = new File(workingDir, "venv\\Scripts\\python.exe");
            if (f.exists()) return f.getAbsolutePath();
        } else {
            File f = new File(workingDir, "venv/bin/python");
            if (f.exists()) return f.getAbsolutePath();
        }
        return "python";
    }

    private String buildDbDsnFromSpringDatasource() {
        String jdbcUrl = environment.getProperty("spring.datasource.url");
        String user = environment.getProperty("spring.datasource.username");
        String pass = environment.getProperty("spring.datasource.password");
        if (jdbcUrl == null || user == null || pass == null) {
            return "";
        }
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            return "";
        }

        String noPrefix = jdbcUrl.substring("jdbc:".length());
        URI uri = URI.create(noPrefix);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String path = uri.getPath() == null ? "" : uri.getPath();
        String db = path.startsWith("/") ? path.substring(1) : path;
        return "postgresql://" + user + ":" + pass + "@" + host + ":" + port + "/" + db;
    }

    private static class Job {
        final String jobId;
        volatile String status = "PENDING";
        volatile Instant startedAt;
        volatile Instant finishedAt;
        volatile Integer exitCode;
        final StringBuilder outputTail = new StringBuilder();

        Job(String jobId) {
            this.jobId = jobId;
        }

        void appendOutput(String s) {
            synchronized (outputTail) {
                outputTail.append(s);
                if (outputTail.length() > OUTPUT_TAIL_LIMIT) {
                    outputTail.delete(0, outputTail.length() - OUTPUT_TAIL_LIMIT);
                }
            }
        }

        SyncJobDto toDto() {
            String tail;
            synchronized (outputTail) {
                tail = outputTail.toString();
            }
            return new SyncJobDto(jobId, status, startedAt, finishedAt, exitCode, tail);
        }
    }
}
