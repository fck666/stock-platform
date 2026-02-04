create extension if not exists pgcrypto;

create schema if not exists iam;

create table if not exists iam.users (
    id uuid primary key default gen_random_uuid(),
    username text not null unique,
    status text not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists iam.identities (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references iam.users (id) on delete cascade,
    provider text not null,
    provider_uid text not null,
    password_hash text,
    created_at timestamptz not null default now(),
    unique (provider, provider_uid)
);

create table if not exists iam.roles (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    name text not null,
    created_at timestamptz not null default now()
);

create table if not exists iam.permissions (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    name text not null,
    created_at timestamptz not null default now()
);

create table if not exists iam.user_roles (
    user_id uuid not null references iam.users (id) on delete cascade,
    role_id uuid not null references iam.roles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, role_id)
);

create table if not exists iam.role_permissions (
    role_id uuid not null references iam.roles (id) on delete cascade,
    permission_id uuid not null references iam.permissions (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (role_id, permission_id)
);

create table if not exists iam.refresh_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references iam.users (id) on delete cascade,
    token_hash text not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);

insert into iam.roles (code, name) values ('admin', '管理员') on conflict (code) do nothing;
insert into iam.roles (code, name) values ('user', '普通用户') on conflict (code) do nothing;

insert into iam.permissions (code, name) values ('data.sync.execute', '执行数据同步') on conflict (code) do nothing;
insert into iam.permissions (code, name) values ('admin.stock.write', '管理股票') on conflict (code) do nothing;
insert into iam.permissions (code, name) values ('admin.index.write', '管理指数') on conflict (code) do nothing;
insert into iam.permissions (code, name) values ('iam.manage', '管理账号与权限') on conflict (code) do nothing;

insert into iam.role_permissions (role_id, permission_id)
select r.id, p.id
from iam.roles r, iam.permissions p
where r.code = 'admin' and p.code in ('data.sync.execute', 'admin.stock.write', 'admin.index.write')
on conflict do nothing;

insert into iam.role_permissions (role_id, permission_id)
select r.id, p.id
from iam.roles r, iam.permissions p
where r.code = 'admin' and p.code in ('iam.manage')
on conflict do nothing;
