create user ${user};

create schema if not exists ${schema} authorization ${user};

alter user ${user} set search_path to ${schema};
