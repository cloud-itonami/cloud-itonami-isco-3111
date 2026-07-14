(ns chemistry.store
  "SSoT for the ISCO-08 3111 lab support actor (chemical and physical
  science technicians). Store is a protocol injected into the
  `chemistry.actor` StateGraph — `MemStore` is the default,
  deterministic, zero-dep backend; a Datomic/kotoba-server-backed
  implementation can be swapped in without touching the actor or
  governor (itonami actor pattern, per ADR-2607011000 / CLAUDE.md
  Actors section).

  Domain:

    sample       — a registered test sample (:sample-id, :project-id,
                   :description, :status)
    test-protocol — a pre-approved, registered analytical test procedure
                   (:protocol-id, :name, :expected-range)
    equipment    — a lab instrument/equipment resource (:equipment-id,
                   :name, :last-calibration)
    record       — a committed lab operation under a sample
                   (test execution, result logging, out-of-spec flag,
                   calibration procedure) — written ONLY via
                   commit-record!, never mutated in place
    ledger       — an append-only audit trail of every proposal/verdict/
                   disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (sample [s sample-id])
  (test-protocol [s protocol-id])
  (equipment [s equipment-id])
  (records-of [s sample-id])
  (ledger [s])
  (register-sample! [s sample])
  (register-test-protocol! [s protocol])
  (register-equipment! [s equipment])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (sample [_ sample-id] (get-in @a [:samples sample-id]))
  (test-protocol [_ protocol-id] (get-in @a [:test-protocols protocol-id]))
  (equipment [_ equipment-id] (get-in @a [:equipment equipment-id]))
  (records-of [_ sample-id] (filter #(= sample-id (:sample-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-sample! [s sample]
    (swap! a assoc-in [:samples (:sample-id sample)] sample) s)
  (register-test-protocol! [s protocol]
    (swap! a assoc-in [:test-protocols (:protocol-id protocol)] protocol) s)
  (register-equipment! [s equipment]
    (swap! a assoc-in [:equipment (:equipment-id equipment)] equipment) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:samples {} :test-protocols {} :equipment {} :records [] :ledger []} seed)))))
