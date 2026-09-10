(ns chemistry.governor
  "LabTechnicianGovernor — the independent safety/traceability layer for
  the ISCO-08 3111 lab support actor (chemical and physical science
  technicians). Wired as its own `:govern` node in `chemistry.actor`'s
  StateGraph, downstream of `:advise` — the Lab Advisor has no notion of
  sample provenance or analytical integrity risk, so this MUST be a
  separate system able to reject a proposal (itonami actor pattern, per
  ADR-2607011000 / CLAUDE.md Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. sample provenance      — the request's sample must be registered.
    2. protocol verification  — :run-test-protocol ops must reference a
                                registered test protocol.
    3. no-actuation           — proposal :effect must be :propose.
    4. equipment-exists       — :calibrate-equipment ops must reference
                                a registered equipment resource.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    5. :flag-out-of-spec-result — always escalates (quality assurance
                                  safeguard, never silently dismissed).
    6. low confidence (< `confidence-floor`)."
  (:require [chemistry.store :as store]))

(def confidence-floor 0.6)
(def ^:private escalating-ops #{:flag-out-of-spec-result})

(defn- hard-violations [{:keys [proposal request]} sample-record protocol-record equipment-record]
  (cond-> []
    (nil? sample-record)
    (conj {:rule :no-sample :detail "未登録 sample"})

    (and (= :run-test-protocol (:op proposal))
         (nil? protocol-record))
    (conj {:rule :no-protocol :detail "run-test-protocol 前に protocol は要登録"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

    (and (= :calibrate-equipment (:op proposal))
         (nil? equipment-record))
    (conj {:rule :no-equipment :detail "calibrate-equipment には登録 equipment が必須"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `chemistry.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [sample-record (store/sample store (:sample-id request))
        protocol-record (when (:protocol-id request) (store/test-protocol store (:protocol-id request)))
        equipment-record (when (:equipment-id request) (store/equipment store (:equipment-id request)))
        hard (hard-violations {:proposal proposal :request request} sample-record protocol-record equipment-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        is-flag? (= :flag-out-of-spec-result (:op proposal))
        risky-op? (and (contains? escalating-ops (:op proposal))
                       is-flag?)]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
