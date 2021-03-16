(ns rf-cljs.networks.network
  (:refer-clojure :exclude [+ - * /])
  (:require
   [rf-cljs.math.operations :refer [+ - * / cos sin sqrt]]
   [rf-cljs.math.complex :refer [real conjugate]]
   [rf-cljs.math.matrix :refer [matrix] :as mat]
   ["mathjs" :as mathjs]))

;; (defmulti abcd :type)

;; (defmethod abcd :series [{:keys [Z]}]
;;   (matrix [[1 Z] [0 1]]))

;; (defmethod abcd :shunt [{:keys [Y]}]
;;   (matrix [[1 0] [Y 1]]))

;; (defmethod abcd :tee [{:keys [Za Zb Zc]}]
;;   (matrix [[(+ 1 (/ Za Zc)) (+ Za Zb (/ (* Za Zb) Zc))]
;;            [(/ 1 Zc) (+ 1 (/ Zb Zc))]]))

;; (defmethod abcd :pi [{:keys [Ya Yb Yc]}]
;;   (matrix [[(+ 1 (/ Yb Yc)) (/ 1 Yc)]
;;            [(+ Ya Yb (/ (* Ya Yb) Yc)) (+ 1 (/ Yb Yc))]]))

;; (defmethod abcd :tline [{:keys [Z0 beta l]}]
;;   (let [j (mathjs/complex "0+1i")
;;         theta (* beta l)]
;;     (matrix [[(cos theta) (* j Z0 (sin theta))]
;;              [(/ (* j (sin theta)) Z0) (cos theta)]])))

;; (defmethod abcd :transformer [{:keys [N]}]
;;   (matrix [[N 0] [0 (/ N)]]))

(defmulti to-s :from)

(defmethod to-s :abcd [{:keys [data Z0]}]
  (let [z01 (if (= (type Z0) mathjs/Matrix)
              (mat/idx Z0 0)
              Z0)
        z02 (if (= (type Z0) mathjs/Matrix)
              (mat/idx Z0 1)
              Z0)]
    (matrix (for [i (range (first (mat/shape data)))]
              (let [A (mat/idx data i 0 0)
                    B (mat/idx data i 0 1)
                    C (mat/idx data i 1 0)
                    D (mat/idx data i 1 1)
                    denom (+ (* A z02) B (* C z01 z02) (* D z01))]
                [[(/ (+ (* A z02) B (* -1 C (conjugate z01) z02) (* -1 D (conjugate z01)))
                     denom)
                  (/ (* 2 (sqrt (* (real z01) (real z02))))
                     denom)]
                 [(/ (* 2 (- (* A D) (* B C)) (sqrt (* (real z01) (real z02))))
                     denom)
                  (/ (+ (* (- A) (conjugate z02)) B (* -1 C z01 (conjugate z02)) (* D z01)))]])))))

(defmethod to-s :z [{:keys [data Z0]}])
(defmethod to-s :y [{:keys [data Z0]}])
(defmethod to-s :s [{:keys [data Z0]}])
(defmethod to-s :t [{:keys [data Z0]}])
(defmethod to-s :h [{:keys [data Z0]}])

(defmulti from-s :to)

(defmethod from-s :abcd [{:keys [data Z0]}])
(defmethod from-s :h [{:keys [data Z0]}])
(defmethod from-s :s [{:keys [data Z0]}] data)
(defmethod from-s :t [{:keys [data Z0]}])
(defmethod from-s :y [{:keys [data Z0]}])
(defmethod from-s :z [{:keys [data Z0]}])

(defn convert [input]
  (from-s (assoc input :data (to-s input))))

(defn passivity [])

(defn reciprocity [s]
  (matrix (for [i (range (first (mat/shape s)))
                :let [s (mat/squeeze (mat/idx s i :all :all))]]
            (- s (mat/transpose s)))))

(defn passive? [])

(defn active? [s])

(defn reciprocal? [])
