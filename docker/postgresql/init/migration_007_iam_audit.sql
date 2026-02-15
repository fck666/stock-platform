
-- Add client_type to refresh_tokens for SSO support
alter table iam.refresh_tokens
add column if not exists client_type text not null default 'desktop';

-- Create audit_logs table
create table if not exists iam.audit_logs (
    id uuid primary key default gen_random_uuid(),
    actor_id uuid references iam.users (id) on delete set null,
    actor_username text,
    target_id uuid references iam.users (id) on delete set null,
    target_username text,
    action text not null,
    details jsonb,
    ip_address text,
    user_agent text,
    created_at timestamptz not null default now()
);

-- Index for faster audit log queries
create index if not exists idx_audit_logs_actor on iam.audit_logs (actor_id);
create index if not exists idx_audit_logs_target on iam.audit_logs (target_id);
create index if not exists idx_audit_logs_action on iam.audit_logs (action);
create index if not exists idx_audit_logs_created_at on iam.audit_logs (created_at);

-- Seed new roles
insert into iam.roles (code, name) values ('super_admin', '超级管理员') on conflict (code) do nothing;
insert into iam.roles (code, name) values ('manager', '经理') on conflict (code) do nothing;
insert into iam.roles (code, name) values ('viewer', '观察员') on conflict (code) do nothing;

-- Seed permissions for new roles if needed (reusing existing ones for now, can add more later)
-- Grant all permissions to super_admin
insert into iam.role_permissions (role_id, permission_id)
select r.id, p.id
from iam.roles r, iam.permissions p
where r.code = 'super_admin'
on conflict do nothing;
