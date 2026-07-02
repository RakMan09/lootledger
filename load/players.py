#!/usr/bin/env python3
"""LootLedger chaos / load client.

Drives the Economy API to (a) benchmark throughput and latency and (b) prove that duplicate
storms never dupe gold. Uses only the Python standard library.

Examples:
    python load/players.py seed --players 100 --gold 1000000
    python load/players.py load --players 100 --requests 20000 --concurrency 64
    python load/players.py dupe-storm --threads 200
"""
import argparse
import json
import statistics
import sys
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib import request, error


def _post(base, path, body, idempotency_key):
    data = json.dumps(body).encode("utf-8")
    req = request.Request(base + path, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Idempotency-Key", idempotency_key)
    start = time.perf_counter()
    try:
        with request.urlopen(req, timeout=30) as resp:
            replayed = resp.headers.get("Idempotent-Replayed")
            return resp.status, replayed, (time.perf_counter() - start)
    except error.HTTPError as e:
        return e.code, None, (time.perf_counter() - start)


def _get(base, path):
    with request.urlopen(base + path, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def seed(base, players, gold):
    print(f"Seeding {players} players with {gold} gold each...")
    with ThreadPoolExecutor(max_workers=32) as pool:
        futures = []
        for pid in range(1, players + 1):
            key = f"seed-{pid}-{gold}"
            body = {"toOwnerId": pid, "asset": "GOLD", "amount": gold}
            futures.append(pool.submit(_post, base, "/admin/mint", body, key))
        ok = sum(1 for f in as_completed(futures) if f.result()[0] in (200, 201))
    print(f"Seeded {ok}/{players} players.")


def load(base, players, requests_count, concurrency):
    print(f"Firing {requests_count} random transfers across {players} players "
          f"at concurrency {concurrency}...")
    latencies = []
    statuses = {}
    lock = threading.Lock()

    def one(i):
        sender = (i % players) + 1
        receiver = ((i + 1) % players) + 1
        key = str(uuid.uuid4())
        body = {"fromOwnerId": sender, "toOwnerId": receiver, "asset": "GOLD", "amount": 1}
        status, _replayed, elapsed = _post(base, "/transfers", body, key)
        with lock:
            latencies.append(elapsed)
            statuses[status] = statuses.get(status, 0) + 1

    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        list(pool.map(one, range(requests_count)))
    wall = time.perf_counter() - start

    latencies.sort()
    def pct(p):
        if not latencies:
            return 0.0
        idx = min(len(latencies) - 1, int(len(latencies) * p))
        return latencies[idx] * 1000

    print(f"\nCompleted {requests_count} requests in {wall:.2f}s")
    print(f"Throughput: {requests_count / wall:,.0f} req/s")
    print(f"Latency ms  p50={pct(0.50):.1f}  p95={pct(0.95):.1f}  "
          f"p99={pct(0.99):.1f}  max={max(latencies) * 1000:.1f}")
    print(f"Status codes: {statuses}")


def dupe_storm(base, threads, sender, receiver, amount):
    """Fire the SAME transfer with the SAME idempotency key from many threads at once."""
    print(f"Dupe storm: {threads} threads, same Idempotency-Key...")
    # Ensure the sender can cover a single transfer.
    _post(base, "/admin/mint", {"toOwnerId": sender, "asset": "GOLD", "amount": amount},
          f"dupe-seed-{uuid.uuid4()}")
    before = _balance(base, receiver)

    key = f"dupe-{uuid.uuid4()}"
    body = {"fromOwnerId": sender, "toOwnerId": receiver, "asset": "GOLD", "amount": amount}
    barrier = threading.Barrier(threads)
    results = []
    lock = threading.Lock()

    def hit():
        barrier.wait()
        status, replayed, _ = _post(base, "/transfers", body, key)
        with lock:
            results.append((status, replayed))

    with ThreadPoolExecutor(max_workers=threads) as pool:
        for _ in range(threads):
            pool.submit(hit)

    after = _balance(base, receiver)
    fresh = sum(1 for s, r in results if r == "false")
    ok = sum(1 for s, r in results if s in (200, 201))
    print(f"Responses: {len(results)}  2xx={ok}  fresh(non-replayed)={fresh}")
    print(f"Receiver balance delta: {after - before} (expected exactly {amount})")
    if fresh == 1 and (after - before) == amount:
        print("PASS: exactly one transfer applied. No dupes. ")
    else:
        print("FAIL: duplication detected!")
        sys.exit(1)


def _balance(base, owner):
    data = _get(base, f"/accounts/{owner}/balances")
    for entry in data.get("balances", []):
        if entry["asset"] == "GOLD":
            return entry["balance"]
    return 0


def main():
    parser = argparse.ArgumentParser(description="LootLedger load/chaos client")
    parser.add_argument("--base", default="http://localhost:8080", help="API base URL")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_seed = sub.add_parser("seed", help="mint gold to players")
    p_seed.add_argument("--players", type=int, default=100)
    p_seed.add_argument("--gold", type=int, default=1_000_000)

    p_load = sub.add_parser("load", help="benchmark transfers")
    p_load.add_argument("--players", type=int, default=100)
    p_load.add_argument("--requests", type=int, default=20_000)
    p_load.add_argument("--concurrency", type=int, default=64)

    p_dupe = sub.add_parser("dupe-storm", help="prove no duplication under concurrent replays")
    p_dupe.add_argument("--threads", type=int, default=200)
    p_dupe.add_argument("--sender", type=int, default=999001)
    p_dupe.add_argument("--receiver", type=int, default=999002)
    p_dupe.add_argument("--amount", type=int, default=100)

    args = parser.parse_args()
    if args.cmd == "seed":
        seed(args.base, args.players, args.gold)
    elif args.cmd == "load":
        load(args.base, args.players, args.requests, args.concurrency)
    elif args.cmd == "dupe-storm":
        dupe_storm(args.base, args.threads, args.sender, args.receiver, args.amount)


if __name__ == "__main__":
    main()
