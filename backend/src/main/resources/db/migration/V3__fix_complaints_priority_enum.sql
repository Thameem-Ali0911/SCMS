-- V3__fix_enum_columns.sql
--
-- Fix ENUM column types to match Hibernate entity definitions.
-- priority in complaints: VARCHAR(10) -> ENUM
-- change_type in complaint_versions: VARCHAR(20) -> ENUM

ALTER TABLE complaints 
MODIFY priority ENUM('low','medium','high','critical') NOT NULL;

ALTER TABLE complaint_versions
MODIFY change_type ENUM('create','update','status_change','assign','resolve','close','soft_delete');
