(ns rf-cljs-test
  (:refer-clojure :exclude [+ - * / < > <= >=])
  (:require-macros [rf-cljs.misc.embed-resource :refer [embed-resource]])
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [rf-cljs.math.operations :refer [+ - * / abs sqrt < > <= >=]]
   [rf-cljs.network.params :as params]
   [rf-cljs.network.params :as touchstone]
   [rf-cljs.math.complex :as cplx]
   [rf-cljs.math.matrix :as mat]))

(def eps 1e-10) ; epsilon used for floating point equality comparisons

; Network Tests 

(def networks (keys params/-param-fn))

(def test-network-size [1 2 2])

(def param-map
  "Map of parameter keywords to parameters. 2x2 parameter matrix is repeated twice
   at top dimension to test "
  {:s     (mat/matrix [[[(cplx/complex 1 0) (cplx/complex -1 0)]
                        [(cplx/complex -1 1) (cplx/complex -1 -1)]]])
   :z     (mat/matrix [[[(cplx/complex -100 -150) (cplx/complex 50 50)]
                        [(cplx/complex 100 0) (cplx/complex -50 0)]]])
   :y     (mat/matrix [[[(cplx/complex 0 0.02) (cplx/complex -0.02 0.02)]
                        [(cplx/complex 0 0.04) (cplx/complex -0.06 0.04)]]])
   :abcd  (mat/matrix [[[(cplx/complex -1 -1.5) (cplx/complex 0 25)]
                        [(cplx/complex 0.01 0) (cplx/complex -0.5 0)]]])
   :h     (mat/matrix [[[(cplx/complex 0 -50) (cplx/complex -1 -1)]
                        [(cplx/complex 2 0) (cplx/complex -0.02 0)]]])
   :t     (mat/matrix [[[(cplx/complex -1 -1) (cplx/complex -0.5 -0.5)]
                        [(cplx/complex 0 -1) (cplx/complex -0.5 -0.5)]]])})

(deftest round-trip-network
  (let [data (mat/random-complex test-network-size)]
    (doseq [neta networks
            netb networks
            z0 [50 [50 (cplx/complex 50 75)]]
            :when (not= neta netb)
            :let [to (params/convert {:from neta :to netb :data data :z0 z0})
                  from (params/convert {:from netb :to neta :data to :z0 z0})]]
      (testing (str "Roundtrip " neta " -> " netb " -> " neta ". Z0 = " z0)
        (is (mat/equals data from eps))))))

(deftest renomalize
  (let [s (mat/random-complex test-network-size)]
    (testing "Renomalize s to 75 and back"
      (is (mat/equals s (params/renormalize (params/renormalize s 50 75) 75 50)) eps))
    (testing "Renomalize complex and back"
      (is (mat/equals s (params/renormalize (params/renormalize s (cplx/complex 1 1) 50) 50 (cplx/complex 1 1)) eps)))))

(deftest to-s-tests
  (doseq [net networks]
    (testing (str net)
      (is (mat/equals (:s param-map) (params/to-s {:from net :data (net param-map) :z0 50}) eps)))))

(deftest from-s-tests
  (doseq [net networks]
    (testing (str net)
      (is (mat/equals (net param-map) (params/from-s {:to net :data (:s param-map) :z0 50}) eps)))))

(deftest even-port
  (doseq [nports [4 6 8]
          :let [data (mat/random-complex [25 nports nports])]
          net [:s :y :z :t]
          z0 [50 (cplx/complex 50 75)]]
    (let [to (params/convert {:from net :to :s :data data :z0 z0})
          from (params/convert {:from :s :to net :data to :z0 z0})]
      (testing (str "Roundtrip " nports " port s -> " net " -> s. Z0 = " z0)
        (is (mat/equals data from eps))))))

(deftest odd-port
  (doseq [nports [3 6 9]
          :let [data (mat/random-complex [25 nports nports])]
          net [:s :y :z]
          z0 [50 (cplx/complex 50 75)]]
    (let [to (params/convert {:from net :to :s :data data :z0 z0})
          from (params/convert {:from :s :to net :data to :z0 z0})]
      (testing (str "Roundtrip " nports "port s -> " net " -> s. Z0 = " z0)
        (is (mat/equals data from eps))))))

; Matrix Function Tests

(def m (mat/matrix [[[1 2] [3 4]] [[5 6] [7 8]]]))
(def true_122 (mat/fill [1 2 2] true))

;   Logical Comparison Operators
(deftest gt-unary
  (is (< m) true_122))

(deftest gt-binary
  (is (< m (+ 1 m)) true_122))

(deftest gt-N-arity
  (is (< m (+ 1 m) (+ 2 m)) true_122))

(deftest lt-unary
  (is (> m) true_122))

(deftest lt-binary
  (is (> (+ 1 m) m) true_122))

(deftest lt-N-arity
  (is (> (+ 2 m) (+ 1 m) m) true_122))

; Touchstone files 

(def test-network {:name "Test Network"
                   :f {:start 1 :stop 5 :n-pts 2 :unit :ghz}
                   :s (:s param-map)
                   :z0 50
                  ;;  :ncm
                   })

(def touchstone-file-list ["test\\touchstone_test_files\\S_DB_50ohm_GHz.s2p"
                           "test\\touchstone_test_files\\S_DB_75ohm_GHz.s2p"
                           "test\\touchstone_test_files\\S_MA_50ohm_GHz.s2p"
                           "test\\touchstone_test_files\\S_MA_75ohm_GHz.s2p"
                           "test\\touchstone_test_files\\S_RI_50ohm_GHz.s2p"
                           "test\\touchstone_test_files\\S_RI_75ohm_GHz.s2p"
                           "test\\touchstone_test_files\\Y_DB_50ohm_MHz.s2p"
                           "test\\touchstone_test_files\\Y_MA_50ohm_MHz.s2p"
                           "test\\touchstone_test_files\\Y_RI_50ohm_MHz.s2p"
                           "test\\touchstone_test_files\\Z_DB_50ohm_kHz.s2p"
                           "test\\touchstone_test_files\\Z_MA_50ohm_kHz.s2p"
                           "test\\touchstone_test_files\\Z_RI_50ohm_kHz.s2p"])


;; (defn test-touchstone-read
;;   (for [fname touchstone-file-list]
;;     (is (network-equals (touchstone/read (read-file fname) test-network))))) 
;;     TODO find place for and implement network-equals
