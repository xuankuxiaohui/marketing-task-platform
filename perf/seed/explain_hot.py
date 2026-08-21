#!/usr/bin/env python3
"""Run EXPLAIN on hot SQL and archive for the slow-query review."""
from __future__ import annotations

import argparse
from pathlib import Path

from seed import mysql

SQL_FILE = Path(__file__).resolve().parent / "explain.sql"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report-dir", required=True)
    args = parser.parse_args()
    report_dir = Path(args.report_dir)
    report_dir.mkdir(parents=True, exist_ok=True)
    statements = [
        block.strip()
        for block in SQL_FILE.read_text().split(";")
        if block.strip() and not all(line.strip().startswith("--") or not line.strip() for line in block.splitlines())
    ]
    chunks = []
    for sql in statements:
        text = mysql(sql)
        chunks.append(sql + "\n" + text + "\n")
    out = report_dir / "slow-query-explain.txt"
    out.write_text("\n".join(chunks))
    print(f"wrote {out} statements={len(statements)}")


if __name__ == "__main__":
    main()
