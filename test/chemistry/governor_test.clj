(ns chemistry.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [chemistry.governor :as governor]
            [chemistry.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-sample! st {:sample-id "sample-1" :project-id "proj-1" :description "Test sample" :status :active})
    (store/register-test-protocol! st {:protocol-id "proto-1" :name "pH Analysis" :expected-range [6.0 8.0]})
    (store/register-equipment! st {:equipment-id "equip-1" :name "pH Meter" :last-calibration "2026-07-13"})
    st))

(deftest rejects-proposal-with-no-sample-registered
  (let [st (fresh-store)
        request {:sample-id "no-such-sample" :protocol-id "proto-1"}
        proposal {:op :run-test-protocol :effect :propose :stake :low :confidence 0.9}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (true? (:hard? verdict)))
    (is (some #(= :no-sample (:rule %)) (:violations verdict)))))

(deftest rejects-proposal-with-no-protocol-for-test-run
  (let [st (fresh-store)
        request {:sample-id "sample-1" :protocol-id "no-such-proto" :op :run-test-protocol}
        proposal {:op :run-test-protocol :effect :propose :stake :low :confidence 0.9}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (true? (:hard? verdict)))
    (is (some #(= :no-protocol (:rule %)) (:violations verdict)))))

(deftest rejects-proposal-with-non-propose-effect
  (let [st (fresh-store)
        request {:sample-id "sample-1" :protocol-id "proto-1" :op :run-test-protocol}
        proposal {:op :run-test-protocol :effect :commit :stake :low :confidence 0.9}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (true? (:hard? verdict)))
    (is (some #(= :no-actuation (:rule %)) (:violations verdict)))))

(deftest approves-clean-test-protocol-run
  (let [st (fresh-store)
        request {:sample-id "sample-1" :protocol-id "proto-1" :op :run-test-protocol}
        proposal {:op :run-test-protocol :effect :propose :stake :low :confidence 0.95}
        verdict (governor/check request {} proposal st)]
    (is (true? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (false? (:escalate? verdict)))))

(deftest escalates-out-of-spec-flag-regardless-of-confidence
  (let [st (fresh-store)
        request {:sample-id "sample-1" :op :flag-out-of-spec-result}
        proposal {:op :flag-out-of-spec-result :effect :propose :stake :high :confidence 0.9}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (true? (:escalate? verdict)))))

(deftest escalates-low-confidence-proposals
  (let [st (fresh-store)
        request {:sample-id "sample-1" :protocol-id "proto-1" :op :run-test-protocol}
        proposal {:op :run-test-protocol :effect :propose :stake :low :confidence 0.5}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (true? (:escalate? verdict)))))

(deftest rejects-calibration-with-no-equipment-registered
  (let [st (fresh-store)
        request {:sample-id "sample-1" :equipment-id "no-such-equip" :op :calibrate-equipment}
        proposal {:op :calibrate-equipment :effect :propose :stake :low :confidence 0.9}
        verdict (governor/check request {} proposal st)]
    (is (false? (:ok? verdict)))
    (is (true? (:hard? verdict)))
    (is (some #(= :no-equipment (:rule %)) (:violations verdict)))))

(deftest approves-calibration-with-registered-equipment
  (let [st (fresh-store)
        request {:sample-id "sample-1" :equipment-id "equip-1" :op :calibrate-equipment}
        proposal {:op :calibrate-equipment :effect :propose :stake :low :confidence 0.95}
        verdict (governor/check request {} proposal st)]
    (is (true? (:ok? verdict)))
    (is (false? (:hard? verdict)))
    (is (false? (:escalate? verdict)))))
