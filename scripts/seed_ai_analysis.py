#!/usr/bin/env python3
"""Generate and load reproducible AI analysis fixtures into the local Compose DB."""

import argparse
import calendar
import json
import subprocess
from datetime import date, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo


ROOT = Path(__file__).resolve().parents[1]
PREFIX = "ai-analysis-fixture:v1:"
PARTNER_NAME = "[AI 더미] 배우자"
CATEGORIES = ["FOOD", "FOOD", "CHILDCARE", "LIVING", "TRANSPORT", "HEALTH", "LEISURE", "OTHER"]
MERCHANTS = {
    "FOOD": "동네 식당", "CHILDCARE": "아기용품점", "LIVING": "생활용품점",
    "TRANSPORT": "교통 이용", "HEALTH": "동네 약국", "LEISURE": "문화생활",
    "OTHER": "기타 구매", "HOUSING": "월세", "EDUCATION": "교육비",
    "FINANCE_INSURANCE": "보험료", "FAMILY_EVENT": "경조사비",
}
BASE_AMOUNTS = {
    "FOOD": 8_000, "CHILDCARE": 25_000, "LIVING": 12_000,
    "TRANSPORT": 3_000, "HEALTH": 6_000, "LEISURE": 15_000,
    "OTHER": 5_000, "HOUSING": 850_000, "EDUCATION": 140_000,
    "FINANCE_INSURANCE": 80_000, "FAMILY_EVENT": 60_000,
}


def month_start(anchor, offset):
    index = anchor.year * 12 + anchor.month - 1 + offset
    return date(index // 12, index % 12 + 1, 1)


def fixtures(anchor):
    rows = []
    for offset, count in [(-2, 84), (-1, 83), (0, 83)]:
        start = month_start(anchor, offset)
        days = anchor.day if offset == 0 else calendar.monthrange(start.year, start.month)[1]
        for index in range(count):
            day = 1 + index * (days - 1) // (count - 1)
            occurred_date = start.replace(day=day)
            time = "12:00:00"
            if index == 0 or occurred_date == anchor:
                time = "00:00:00"
            elif index == count - 1:
                time = "23:59:59"
            category = ["HOUSING", "FINANCE_INSURANCE", "EDUCATION", "FAMILY_EVENT"][index] if index < 4 else CATEGORIES[(index - 4) % len(CATEGORIES)]
            amount = BASE_AMOUNTS[category]
            if index >= 4:
                amount += (index % 5) * 1_000
                if category == "FOOD":
                    amount += (offset + 2) * 3_000
            rows.append({
                "description": f"{PREFIX}{anchor.isoformat()}:{len(rows) + 1:03d}",
                "merchant": f"[AI 더미] {MERCHANTS[category]}",
                "amount": amount,
                "category": category,
                "payer": "ME" if len(rows) % 2 == 0 else "PARTNER",
                "payment_method": "CASH" if index % 4 == 0 else "CARD",
                "occurred_at": f"{occurred_date.isoformat()}T{time}+09:00",
            })
    return rows


def scenarios(anchor):
    current = month_start(anchor, 0)
    previous = month_start(anchor, -1)
    oldest = month_start(anchor, -2)
    previous_end = current - timedelta(days=1)
    return [
        {"question": "이번 달 총 얼마 썼어?", "from": current, "to": anchor},
        {"question": "지난달 총 얼마 썼어?", "from": previous, "to": previous_end},
        {"question": "지난달보다 이번 달 식비가 얼마나 늘었어?", "from": current, "to": anchor, "category": "FOOD", "compare_from": previous, "compare_to": previous_end},
        {"question": "이번 달 내가 쓴 식비는 얼마야?", "from": current, "to": anchor, "category": "FOOD", "payer": "ME"},
        {"question": "이번 달 배우자가 쓴 육아비는 얼마야?", "from": current, "to": anchor, "category": "CHILDCARE", "payer": "PARTNER"},
        {"question": "이번 달 가장 많이 쓴 카테고리는 뭐야?", "from": current, "to": anchor, "top_category": True},
        {"question": "지지난달 총 지출은 얼마야?", "from": oldest, "to": previous - timedelta(days=1)},
        {"question": f"{previous_end.isoformat()} 하루에 총 얼마 썼어?", "from": previous_end, "to": previous_end},
        {"question": f"{oldest.isoformat()}부터 {anchor.isoformat()}까지 총 얼마 썼어?", "from": oldest, "to": anchor},
        {"question": f"{month_start(anchor, -5).isoformat()}부터 {(month_start(anchor, -4) - timedelta(days=1)).isoformat()}까지 총 얼마 썼어?", "from": month_start(anchor, -5), "to": month_start(anchor, -4) - timedelta(days=1)},
    ]


def expected(rows, scenario):
    selected = [row for row in rows if
                scenario["from"].isoformat() <= row["occurred_at"][:10] <= scenario["to"].isoformat()
                and (scenario.get("category") is None or row["category"] == scenario["category"])
                and (scenario.get("payer") is None or row["payer"] == scenario["payer"])]
    result = {"total": sum(row["amount"] for row in selected), "count": len(selected)}
    if scenario.get("top_category"):
        totals = {}
        for row in selected:
            totals[row["category"]] = totals.get(row["category"], 0) + row["amount"]
        result["top_category"] = max(totals, key=totals.get) if totals else None
    if "compare_from" in scenario:
        previous_rows = [row for row in rows if scenario["compare_from"].isoformat() <= row["occurred_at"][:10] <= scenario["compare_to"].isoformat() and row["category"] == scenario["category"]]
        result["previous_total"] = sum(row["amount"] for row in previous_rows)
        result["change"] = result["total"] - result["previous_total"]
    return result


def sql_literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def seed_sql(anchor, rows, questions, household_id, me_id, partner_id, create_partner):
    payload = sql_literal(json.dumps(rows, ensure_ascii=False))
    reports = []
    checks = []
    for index, scenario in enumerate(questions, 1):
        def query(start, end):
            category = scenario.get("category")
            payer = scenario.get("payer")
            conditions = ["t.household_id = c.household_id", f"t.occurred_at >= {sql_literal(start)}::timestamp AT TIME ZONE 'Asia/Seoul'", f"t.occurred_at < ({sql_literal(end)}::date + 1)::timestamp AT TIME ZONE 'Asia/Seoul'"]
            if category:
                conditions.append(f"t.category = {sql_literal(category)}")
            if payer:
                conditions.append(f"t.payer_id = c.{'me_id' if payer == 'ME' else 'partner_id'}")
            return " AND ".join(conditions)
        conditions = query(scenario["from"], scenario["to"])
        result = expected(rows, scenario)
        fixture_condition = f"t.description LIKE {sql_literal(PREFIX + anchor.isoformat() + ':%')}"
        checks.append(f"""IF (SELECT COALESCE(SUM(t.amount), 0) FROM seed_context c JOIN transactions t ON {conditions} AND {fixture_condition}) != {result['total']}
            OR (SELECT COUNT(*) FROM seed_context c JOIN transactions t ON {conditions} AND {fixture_condition}) != {result['count']} THEN
            RAISE EXCEPTION 'fixture expectation mismatch for question {index}'; END IF;""")
        reports.append(f"SELECT {index} AS question, COALESCE(SUM(t.amount), 0) AS total, COUNT(t.id) AS count FROM seed_context c LEFT JOIN transactions t ON {conditions};")
        if "compare_from" in scenario:
            reports.append(f"SELECT '3_previous' AS question, COALESCE(SUM(t.amount), 0) AS total, COUNT(t.id) AS count FROM seed_context c LEFT JOIN transactions t ON {query(scenario['compare_from'], scenario['compare_to'])};")
        if scenario.get("top_category"):
            reports.append(f"SELECT t.category, SUM(t.amount) AS total FROM seed_context c JOIN transactions t ON {conditions} GROUP BY t.category ORDER BY total DESC, t.category LIMIT 1;")
    return rf"""\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(730251, {household_id});
CREATE TEMP TABLE seed_context (household_id bigint, me_id bigint, partner_id bigint) ON COMMIT DROP;
INSERT INTO seed_context VALUES ({household_id}, {me_id}, {partner_id if partner_id is not None else 'NULL'});
DO $seed$
DECLARE c record; partner bigint;
BEGIN
    SELECT * INTO c FROM seed_context;
    IF NOT EXISTS (SELECT 1 FROM household_memberships WHERE household_id = c.household_id AND user_id = c.me_id) THEN
        RAISE EXCEPTION 'me-id must belong to the selected household';
    END IF;
    IF c.partner_id IS NULL THEN
        IF NOT {'true' if create_partner else 'false'} THEN
            RAISE EXCEPTION 'provide partner-id or create-dummy-partner';
        END IF;
        SELECT u.id INTO partner FROM users u JOIN household_memberships m ON m.user_id = u.id
        WHERE m.household_id = c.household_id AND u.display_name = {sql_literal(PARTNER_NAME)}
          AND NOT EXISTS (SELECT 1 FROM auth_identities a WHERE a.user_id = u.id);
        IF partner IS NULL THEN
            IF (SELECT COUNT(*) FROM household_memberships WHERE household_id = c.household_id) != 1 THEN
                RAISE EXCEPTION 'household already has multiple members; specify partner-id';
            END IF;
            INSERT INTO users (display_name) VALUES ({sql_literal(PARTNER_NAME)}) RETURNING id INTO partner;
            INSERT INTO household_memberships (household_id, user_id) VALUES (c.household_id, partner);
        END IF;
        UPDATE seed_context SET partner_id = partner;
    END IF;
    SELECT * INTO c FROM seed_context;
    IF c.me_id = c.partner_id OR NOT EXISTS (
        SELECT 1 FROM household_memberships WHERE household_id = c.household_id AND user_id = c.partner_id
    ) THEN
        RAISE EXCEPTION 'partner-id must be a different member of the selected household';
    END IF;
    IF EXISTS (SELECT 1 FROM transactions WHERE household_id = c.household_id
        AND description LIKE {sql_literal(PREFIX + '%')}
        AND description NOT LIKE {sql_literal(PREFIX + anchor.isoformat() + ':%')}) THEN
        RAISE EXCEPTION 'another fixture date exists; remove that fixture explicitly before changing as-of';
    END IF;
END $seed$;
CREATE TEMP TABLE seed_rows ON COMMIT DROP AS
SELECT * FROM jsonb_to_recordset({payload}::jsonb) AS r(
    description text, merchant text, amount bigint, category text, payer text, payment_method text, occurred_at timestamptz
);
INSERT INTO transactions (household_id, payer_id, merchant, description, amount, category,
    classification_source, classification_confidence, classification_confirmed_at,
    payment_method, card_issuer, occurred_at, created_at, updated_at)
SELECT c.household_id, CASE WHEN r.payer = 'ME' THEN c.me_id ELSE c.partner_id END,
    r.merchant, r.description, r.amount, r.category, 'USER', 'HIGH', r.occurred_at,
    r.payment_method, CASE WHEN r.payment_method = 'CARD' THEN 'SHINHAN' END,
    r.occurred_at, r.occurred_at, r.occurred_at
FROM seed_rows r CROSS JOIN seed_context c
WHERE NOT EXISTS (SELECT 1 FROM transactions t WHERE t.household_id = c.household_id AND t.description = r.description);
DO $verify$
BEGIN
{chr(10).join(checks)}
END $verify$;
SELECT c.household_id, c.me_id, c.partner_id,
    (SELECT COUNT(*) FROM transactions t WHERE t.household_id = c.household_id
        AND t.description LIKE {sql_literal(PREFIX + anchor.isoformat() + ':%')}) AS fixture_count,
    (SELECT COUNT(*) FROM transactions t WHERE t.household_id = c.household_id
        AND (t.description IS NULL OR t.description NOT LIKE {sql_literal(PREFIX + '%')})) AS other_transaction_count
FROM seed_context c;
\echo Actual household expectations including existing transactions:
{chr(10).join(reports)}
COMMIT;
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--as-of", type=date.fromisoformat, default=datetime.now(ZoneInfo("Asia/Seoul")).date())
    parser.add_argument("--apply", action="store_true", help="insert into the repository's local Compose postgres service")
    parser.add_argument("--household-id", type=int)
    parser.add_argument("--me-id", type=int)
    partner = parser.add_mutually_exclusive_group()
    partner.add_argument("--partner-id", type=int)
    partner.add_argument("--create-dummy-partner", action="store_true")
    args = parser.parse_args()
    if args.apply and (not args.household_id or not args.me_id or not (args.partner_id or args.create_dummy_partner)):
        parser.error("--apply requires household-id, me-id and either partner-id or create-dummy-partner")
    if any(value is not None and value <= 0 for value in [args.household_id, args.me_id, args.partner_id]):
        parser.error("IDs must be positive")
    rows = fixtures(args.as_of)
    questions = scenarios(args.as_of)
    print(f"기준 날짜: {args.as_of}; 가상 거래 {len(rows)}건. 아래 기대값은 더미 데이터만의 결과입니다.", flush=True)
    for number, scenario in enumerate(questions, 1):
        print(f"{number}. {scenario['question']} {json.dumps(expected(rows, scenario), ensure_ascii=False)}", flush=True)
    if args.apply:
        command = ["docker", "compose", "--env-file", "/dev/null", "-f", str(ROOT / "compose.yaml"),
                   "exec", "-T", "postgres", "psql", "-U", "woorijip", "-d", "woorijip", "-X"]
        result = subprocess.run(command, input=seed_sql(args.as_of, rows, questions, args.household_id, args.me_id, args.partner_id, args.create_dummy_partner), text=True, cwd=ROOT)
        raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
