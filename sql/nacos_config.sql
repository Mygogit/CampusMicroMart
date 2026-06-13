/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info   */
/******************************************/
CREATE TABLE IF NOT EXISTS config_info (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'id',
    data_id VARCHAR(255) NOT NULL COMMENT 'data_id',
    group_id VARCHAR(128) DEFAULT NULL COMMENT 'group_id',
    content LONGTEXT NOT NULL COMMENT 'content',
    md5 VARCHAR(32) DEFAULT NULL COMMENT 'md5',
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'modified time',
    src_user TEXT COMMENT 'source user',
    src_ip VARCHAR(50) DEFAULT NULL COMMENT 'source ip',
    app_name VARCHAR(128) DEFAULT NULL COMMENT 'app name',
    tenant_id VARCHAR(128) DEFAULT '' COMMENT 'tenant id',
    c_desc VARCHAR(256) DEFAULT NULL COMMENT 'description',
    c_use VARCHAR(64) DEFAULT NULL COMMENT 'use',
    effect VARCHAR(64) DEFAULT NULL COMMENT 'effect',
    type VARCHAR(64) DEFAULT NULL COMMENT 'type',
    c_schema TEXT COMMENT 'schema',
    encrypted_data_key TEXT NOT NULL COMMENT 'secret key',
    PRIMARY KEY (id),
    UNIQUE KEY uk_configinfo_datagrouptenant (data_id, group_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info';

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info_aggr   */
/******************************************/
CREATE TABLE IF NOT EXISTS config_info_aggr (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'id',
    data_id VARCHAR(255) NOT NULL COMMENT 'data_id',
    group_id VARCHAR(128) NOT NULL COMMENT 'group_id',
    datum_id VARCHAR(256) NOT NULL COMMENT 'datum_id',
    content LONGTEXT NOT NULL COMMENT 'content',
    gmt_modified DATETIME NOT NULL COMMENT 'modified time',
    app_name VARCHAR(128) DEFAULT NULL COMMENT 'app name',
    tenant_id VARCHAR(128) DEFAULT '' COMMENT 'tenant id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_configinfoaggr_datagrouptenant (data_id, group_id, datum_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info_aggr';

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info_beta   */
/******************************************/
CREATE TABLE IF NOT EXISTS config_info_beta (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'id',
    data_id VARCHAR(255) NOT NULL COMMENT 'data_id',
    group_id VARCHAR(128) NOT NULL COMMENT 'group_id',
    app_name VARCHAR(128) DEFAULT NULL COMMENT 'app_name',
    content LONGTEXT NOT NULL COMMENT 'content',
    beta_ips VARCHAR(1024) DEFAULT NULL COMMENT 'betaIps',
    md5 VARCHAR(32) DEFAULT NULL COMMENT 'md5',
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'modified time',
    src_user TEXT COMMENT 'source user',
    src_ip VARCHAR(50) DEFAULT NULL COMMENT 'source ip',
    tenant_id VARCHAR(128) DEFAULT '' COMMENT 'tenant id',
    encrypted_data_key TEXT NOT NULL COMMENT 'secret key',
    PRIMARY KEY (id),
    UNIQUE KEY uk_configinfobeta_datagrouptenant (data_id, group_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info_beta';

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info_tag   */
/******************************************/
CREATE TABLE IF NOT EXISTS config_info_tag (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'id',
    data_id VARCHAR(255) NOT NULL COMMENT 'data_id',
    group_id VARCHAR(128) NOT NULL COMMENT 'group_id',
    tenant_id VARCHAR(128) DEFAULT '' COMMENT 'tenant_id',
    tag_id VARCHAR(128) NOT NULL COMMENT 'tag_id',
    app_name VARCHAR(128) DEFAULT NULL COMMENT 'app_name',
    content LONGTEXT NOT NULL COMMENT 'content',
    md5 VARCHAR(32) DEFAULT NULL COMMENT 'md5',
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'modified time',
    src_user TEXT COMMENT 'source user',
    src_ip VARCHAR(50) DEFAULT NULL COMMENT 'source ip',
    PRIMARY KEY (id),
    UNIQUE KEY uk_configinfotag_datagrouptenanttag (data_id, group_id, tenant_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info_tag';

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_tags_relation   */
/******************************************/
CREATE TABLE IF NOT EXISTS config_tags_relation (
    id BIGINT NOT NULL COMMENT 'id',
    tag_name VARCHAR(128) NOT NULL COMMENT 'tag_name',
    tag_type VARCHAR(64) DEFAULT NULL COMMENT 'tag_type',
    data_id VARCHAR(255) NOT NULL COMMENT 'data_id',
    group_id VARCHAR(128) NOT NULL COMMENT 'group_id',
    tenant_id VARCHAR(128) DEFAULT '' COMMENT 'tenant_id',
    nid BIGINT NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (nid),
    UNIQUE KEY uk_configtagrelation_configidtag (id, tag_name, tag_type),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_tag_relation';

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = group_capacity   */
/******************************************/
CREATE TABLE IF NOT EXISTS group_capacity (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key id',
    group_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Group ID',
    quota INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'quota',
    `usage` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'usage',
    max_size INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'max size of config',
    max_aggr_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'max count of aggregated config data',
    max_aggr_size INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'max size of aggregated config data',
    max_history_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'max count of history records',
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'modified time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_id (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='group capacity table';

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = his_config_info   */
/******************************************/
CREATE TABLE IF NOT EXISTS his_config_info (
    id BIGINT UNSIGNED NOT NULL,
    nid BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    data_id VARCHAR(255) NOT NULL,
    group_id VARCHAR(128) NOT NULL,
    app_name VARCHAR(128) DEFAULT NULL COMMENT 'app_name',
    content LONGTEXT NOT NULL,
    md5 VARCHAR(32) DEFAULT NULL,
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    src_user TEXT,
    src_ip VARCHAR(50) DEFAULT NULL,
    op_type CHAR(10) DEFAULT NULL,
    tenant_id VARCHAR(128) DEFAULT '' COMMENT 'tenant_id',
    encrypted_data_key TEXT NOT NULL COMMENT 'secret key',
    PRIMARY KEY (nid),
    KEY idx_gmt_create (gmt_create),
    KEY idx_gmt_modified (gmt_modified),
    KEY idx_did (data_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='his_config_info';

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = tenant_capacity   */
/******************************************/
CREATE TABLE IF NOT EXISTS tenant_capacity (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key id',
    tenant_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Tenant ID',
    quota INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'quota',
    `usage` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'usage',
    max_size INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'max size of config',
    max_aggr_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'max count of aggregated config data',
    max_aggr_size INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'max size of aggregated config data',
    max_history_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'max count of history records',
    gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'modified time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='tenant capacity table';

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = tenant_info   */
/******************************************/
CREATE TABLE IF NOT EXISTS tenant_info (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'id',
    kp VARCHAR(128) NOT NULL COMMENT 'kp',
    tenant_id VARCHAR(128) DEFAULT '' COMMENT 'tenant_id',
    tenant_name VARCHAR(128) DEFAULT '' COMMENT 'tenant_name',
    tenant_desc VARCHAR(256) DEFAULT NULL COMMENT 'tenant_desc',
    create_source VARCHAR(32) DEFAULT NULL COMMENT 'create_source',
    gmt_create BIGINT NOT NULL COMMENT 'created time',
    gmt_modified BIGINT NOT NULL COMMENT 'modified time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_info_kptenantid (kp, tenant_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='tenant_info';

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = users   */
/******************************************/
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) NOT NULL PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL
);

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = roles   */
/******************************************/
CREATE TABLE IF NOT EXISTS roles (
    username VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    UNIQUE KEY uk_username_role (username, role)
);

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = permissions   */
/******************************************/
CREATE TABLE IF NOT EXISTS permissions (
    role VARCHAR(50) NOT NULL,
    resource VARCHAR(255) NOT NULL,
    action VARCHAR(8) NOT NULL,
    UNIQUE KEY uk_role_permission (role, resource, action)
);

/******************************************/
/*   初始化用户数据   */
/******************************************/
INSERT IGNORE INTO users (username, password, enabled) VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkdrxfvUu', TRUE);
INSERT IGNORE INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');
