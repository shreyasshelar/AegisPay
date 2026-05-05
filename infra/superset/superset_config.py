"""
AegisPay Superset Configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Placed at /app/pythonpath/superset_config.py inside the container.
Superset loads this file automatically when PYTHONPATH includes /app/pythonpath.

Reference: https://superset.apache.org/docs/configuration/configuring-superset
"""

import os
from celery.schedules import crontab

# ── Core security ─────────────────────────────────────────────────────────────

SECRET_KEY = os.environ.get(
    "SUPERSET_SECRET_KEY",
    "aegispay-superset-dev-secret-change-in-prod"
)

# ── Metadata database (Superset's own state) ──────────────────────────────────

_db_user = os.environ.get("DATABASE_USER", "superset")
_db_pass = os.environ.get("DATABASE_PASSWORD", "superset")
_db_host = os.environ.get("DATABASE_HOST", "superset-db")
_db_port = os.environ.get("DATABASE_PORT", "5432")
_db_name = os.environ.get("DATABASE_DB", "superset")

SQLALCHEMY_DATABASE_URI = (
    f"postgresql+psycopg2://{_db_user}:{_db_pass}@{_db_host}:{_db_port}/{_db_name}"
)

# ── Feature flags ─────────────────────────────────────────────────────────────

FEATURE_FLAGS = {
    "ENABLE_TEMPLATE_PROCESSING": True,   # Jinja2 in SQL queries (for date filters)
    "DASHBOARD_NATIVE_FILTERS": True,     # Native cross-filter dashboards
    "DASHBOARD_CROSS_FILTERS": True,
    "GLOBAL_ASYNC_QUERIES": False,        # Keep sync for dev simplicity
    "ALERT_REPORTS": True,                # Email/Slack alerts from charts
    "THUMBNAILS": False,                  # Disable thumbnail generation (saves resources)
    "LISTVIEWS_DEFAULT_CARD_VIEW": False,
}

# ── ClickHouse connection (pre-registered data source) ────────────────────────
# Superset will auto-add this DB on first init via DATABASES config
# The clickhouse-connect driver must be installed: pip install clickhouse-connect

CLICKHOUSE_URL = os.environ.get(
    "CLICKHOUSE_URL",
    "clickhouse+native://default:@clickhouse:9000/aegispay_analytics"
)

# ── Row limit safety ──────────────────────────────────────────────────────────

ROW_LIMIT = 50_000           # Max rows returned by a chart query
VIZ_ROW_LIMIT = 10_000       # Max rows for visualizations
SAMPLES_ROW_LIMIT = 1_000    # Max rows for "Sample Data"

# ── WTF CSRF ──────────────────────────────────────────────────────────────────

WTF_CSRF_ENABLED = True
WTF_CSRF_EXEMPT_LIST = []
WTF_CSRF_TIME_LIMIT = 60 * 60 * 24 * 365  # 1 year (relaxed for dev)

# ── HTTP / CORS ───────────────────────────────────────────────────────────────

ENABLE_CORS = True
CORS_OPTIONS = {
    "supports_credentials": True,
    "allow_headers": ["*"],
    "resources": ["*"],
    "origins": ["http://localhost:8088", "http://localhost:3000"],
}

# ── Celery (async task queue — disabled in dev) ───────────────────────────────

class CeleryConfig:
    broker_url = "redis://redis:6379/2"
    result_backend = "redis://redis:6379/3"
    worker_prefetch_multiplier = 1
    task_acks_late = True
    beat_schedule = {
        "reports.scheduler": {
            "task": "superset.tasks.scheduler.schedule_heartbeat",
            "schedule": crontab(minute="*", hour="*"),
        },
    }


CELERY_CONFIG = CeleryConfig

# ── Logging ───────────────────────────────────────────────────────────────────

import logging

LOG_LEVEL = logging.INFO
ENABLE_TIME_ROTATE = False

# ── Cache (uses Redis for query results) ──────────────────────────────────────

_redis_host = os.environ.get("REDIS_HOST", "redis")
_redis_port = os.environ.get("REDIS_PORT", "6379")

CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 300,
    "CACHE_KEY_PREFIX": "superset_",
    "CACHE_REDIS_URL": f"redis://{_redis_host}:{_redis_port}/1",
}

DATA_CACHE_CONFIG = CACHE_CONFIG

# ── Email alerts (optional — set SMTP env vars to enable) ────────────────────

SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", "587"))
SMTP_STARTTLS = True
SMTP_SSL = False
SMTP_USER = os.environ.get("SMTP_USER", "")
SMTP_PASSWORD = os.environ.get("SMTP_PASSWORD", "")
SMTP_MAIL_FROM = os.environ.get("SMTP_FROM", "alerts@aegispay.io")
