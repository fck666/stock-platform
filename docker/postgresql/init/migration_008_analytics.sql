create schema if not exists analytics;

create table if not exists analytics.page_views (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references iam.users (id) on delete set null,
    username text,
    path text not null,
    title text,
    created_at timestamptz not null default now()
);

create index if not exists idx_page_views_created_at on analytics.page_views (created_at);
create index if not exists idx_page_views_user on analytics.page_views (user_id);
create index if not exists idx_page_views_path on analytics.page_views (path);

create table if not exists analytics.api_calls (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references iam.users (id) on delete set null,
    username text,
    method text not null,
    path text not null,
    status_code int not null,
    latency_ms int not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_api_calls_created_at on analytics.api_calls (created_at);
create index if not exists idx_api_calls_user on analytics.api_calls (user_id);
create index if not exists idx_api_calls_path on analytics.api_calls (path);
create index if not exists idx_api_calls_method_path on analytics.api_calls (method, path);
create index if not exists idx_api_calls_status on analytics.api_calls (status_code);

alter table iam.audit_logs add column if not exists request_id text;
alter table iam.audit_logs add column if not exists http_method text;
alter table iam.audit_logs add column if not exists route text;
alter table iam.audit_logs add column if not exists status_code int;
alter table iam.audit_logs add column if not exists latency_ms int;

create index if not exists idx_audit_logs_route on iam.audit_logs (route);
create index if not exists idx_audit_logs_request_id on iam.audit_logs (request_id);

insert into iam.permissions (code, name) values ('admin.analytics.read', '查看行为看板') on conflict (code) do nothing;
insert into iam.role_permissions (role_id, permission_id)
select r.id, p.id
from iam.roles r, iam.permissions p
where r.code in ('admin', 'super_admin') and p.code = 'admin.analytics.read'
on conflict do nothing;
