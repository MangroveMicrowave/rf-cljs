(ns rf-cljs.network.params
  (:refer-clojure :exclude [+ - * / < > <= >=])
  (:require
   [rf-cljs.math.operations :refer [+ - * / abs sqrt < > <= >=]]
   [rf-cljs.math.complex :as cmplx]
   [rf-cljs.math.matrix :as mat]
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

;; (defmethod abcd :tline [{:keys [z0 beta l]}]
;;   (let [j (mathjs/complex "0+1i")
;;         theta (* beta l)]
;;     (matrix [[(cos theta) (* j z0 (sin theta))]
;;              [(/ (* j (sin theta)) z0) (cos theta)]])))

;; (defmethod abcd :transformer [{:keys [N]}]
;;   (matrix [[N 0] [0 (/ N)]]))

(defn two-port? [data]
  (let [[_ n _] (mat/shape data)]
    (= n 2)))

(defn -fix-z0-shape [z0 [a b c]]
  (if (nil? c)
    (assert (= a b) "Matrix must be square")
    (assert (= b c) "Matrix must be sqaure"))
  (if (or (mat/matrix? z0)
          (vector? z0))
    (let [z0 (mat/matrix z0)]
      (assert (= [b] (mat/shape z0)) (str "(count z0) must equal nports:\n [nports]: "
                                          [b] "\n(mat/shape z0): " (mat/shape z0)))
      z0)
    (* z0 (mat/ones [b]))))

(defn -internal-external-partition
  "Partition the matrix of 2n ports into internal-external blocks
  Returns [ee ei ie ii]
  http://www.microwave.fr/publications/151.pdf"
  [data]
  (let [[m n] (mat/shape data)
        part-n (quot n 2)]
    (assert (= 0 (mod m 2)) "Matrix must be 2nx2n")
    (if (= n 2)
      [(mat/idx data 0 0)
       (mat/idx data 0 1)
       (mat/idx data 1 0)
       (mat/idx data 1 1)]
      [(mat/idx data (range part-n) (range part-n))
       (mat/idx data (range part-n) (range part-n n))
       (mat/idx data (range part-n n) (range part-n))
       (mat/idx data (range part-n n) (range part-n n))])))

(defn -fix-z0-and-broadcast [f data z0]
  (let [shape (mat/shape data)
        z0 (-fix-z0-shape z0 shape)]
    (case (count shape)
      2 (f data z0)
      3 (mat/broadcast data #(f % z0) 0))))

(defn -destructure-two-port [m]
  (assert (two-port? m) "Parameters must be two port")
  [(mat/idx m 0 0) (mat/idx m 0 1)
   (mat/idx m 1 0) (mat/idx m 1 1)])

(defn -z2s [Z z0]
  (let [G (mat/diag z0)
        F (mat/diag (-> (cmplx/real z0)
                        abs
                        sqrt
                        mat/dot-divide
                        (* 0.5)))]
    (* F (- Z (mat/ctranspose G)) (mat/inv (+ Z G)) (mat/inv F))))

(defn -s2z [S z0]
  (let [n (first (mat/shape S))
        G (mat/diag z0)
        F (mat/diag (-> (cmplx/real z0)
                        abs
                        sqrt
                        mat/dot-divide
                        (* 0.5)))]
    (* (mat/inv F) (mat/inv (- (mat/eye n) S)) (+ (* S G) (mat/ctranspose G)) F)))

(defn -y2s [Y z0]
  (let [n (first (mat/shape Y))
        G (mat/diag z0)
        F (mat/diag (-> (cmplx/real z0)
                        abs
                        sqrt
                        mat/dot-divide
                        (* 0.5)))]
    (* F (- (mat/eye n) (* (cmplx/conjugate G) Y)) (mat/inv (+ (mat/eye n) (* G Y))) (mat/inv F))))

(defn -s2y [S z0]
  (let [n (first (mat/shape S))
        G (mat/diag z0)
        F (mat/diag (-> (cmplx/real z0)
                        abs
                        sqrt
                        mat/dot-divide
                        (* 0.5)))]
    (* (mat/inv F) (mat/inv (+ (* S G) (cmplx/conjugate G))) (- (mat/eye n) S) F)))

(defn -a2s [ABCD z0]
  (let [z01 (mat/idx z0 0)
        z02 (mat/idx z0 1)
        [A B C D] (-destructure-two-port ABCD)
        denom (+ (* A z02) B (* C z01 z02) (* D z01))]
    (mat/matrix [[(/ (+ (* A z02) B (* -1 C (cmplx/conjugate z01) z02) (* -1 D (cmplx/conjugate z01)))
                     denom)
                  (/ (* 2 (- (* A D) (* B C)) (sqrt (* (cmplx/real z01) (cmplx/real z02))))
                     denom)]
                 [(/ (* 2 (sqrt (* (cmplx/real z01) (cmplx/real z02))))
                     denom)
                  (/ (+ (* (- A) (cmplx/conjugate z02)) B (* -1 C z01 (cmplx/conjugate z02)) (* D z01))
                     denom)]])))

(defn -s2a [S z0]
  (let [z01 (mat/idx z0 0)
        z02 (mat/idx z0 1)
        [s11 s12 s21 s22] (-destructure-two-port S)
        denom  (* 2 s21 (sqrt (* (cmplx/real z01) (cmplx/real z02))))]
    (mat/matrix [[(/ (+ (* (+ (cmplx/conjugate z01) (* s11 z01)) (- 1 s22)) (* s12 s21 z01)) denom)
                  (/ (- (* (+ (cmplx/conjugate z01) (* s11 z01)) (+ (cmplx/conjugate z02) (* s22 z02)))
                        (* s12 s21 z01 z02)) denom)]
                 [(/ (- (* (- 1 s11) (- 1 s22)) (* s21 s12)) denom)
                  (/ (+ (* (- 1 s11) (+ (cmplx/conjugate z02) (* s22 z02))) (* s12 s21 z02)) denom)]])))

(defn -h2s [H z0]
  (let [z01 (mat/idx z0 0)
        z02 (mat/idx z0 1)
        [h11 h12 h21 h22] (-destructure-two-port H)
        denom (- (* (+ z01 h11) (+ 1 (* h22 z02))) (* h12 h21 z02))]
    (mat/matrix [[(/ (- (* (- h11 (cmplx/conjugate z01)) (+ 1 (* h22 z02))) (* h12 h21 z02))
                     denom)
                  (/ (* 2 h12 (sqrt (* (cmplx/real z01) (cmplx/real z02))))
                     denom)]
                 [(/ (* -2 h21 (sqrt (* (cmplx/real z01) (cmplx/real z02))))
                     denom)
                  (/ (+ (* (+ z01 h11) (- 1 (* h22 (cmplx/conjugate z02)))) (* h12 h21 (cmplx/conjugate z02)))
                     denom)]])))

(defn -s2h [S z0]
  (let [z01 (mat/idx z0 0)
        z02 (mat/idx z0 1)
        [s11 s12 s21 s22] (-destructure-two-port S)
        denom (+ (* (- 1 s11) (+ (cmplx/conjugate z02) (* s22 z02))) (* s12 s21 z02))]
    (mat/matrix [[(/ (- (* (+ (cmplx/conjugate z01) (* s11 z01)) (+ (cmplx/conjugate z02) (* s22 z02))) (* s12 s21 z01 z02))
                     denom)
                  (/ (* 2 s12 (sqrt (* (cmplx/real z01) (cmplx/real z02))))
                     denom)]
                 [(/ (* -2 s21 (sqrt (* (cmplx/real z01) (cmplx/real z02))))
                     denom)
                  (/ (- (* (- 1 s11) (- 1 s22)) (* s12 s21))
                     denom)]])))

(defn -t2s [T]
  (let [[T11 T12 T21 T22] (-internal-external-partition T)]
    (mat/matrix [[(* T12 (mat/inv T22)) (- T11 (* T12 (mat/inv T22) T21))]
                 [(mat/inv T22) (* -1 (mat/inv T22) T21)]])))

(defn -s2t [S]
  (let [[S11 S12 S21 S22] (-internal-external-partition S)]
    (mat/matrix [[(- S12 (* S11 (mat/inv S21) S22)) (* S11 (mat/inv S21))]
                 [(* (mat/inv (- S21)) S22) (mat/inv S21)]])))

(def -param-fn
  {:abcd {:from -a2s :to -s2a}
   :s {:from identity :to identity}
   :z {:from -z2s :to -s2z}
   :y {:from -y2s :to -s2y}
   :h {:from -h2s :to -s2h}
   :t {:from -t2s :to -s2t}})

(defn to-s [{:keys [from data z0]}]
  (-fix-z0-and-broadcast (:from (from -param-fn)) data z0))

(defn from-s [{:keys [to data z0]}]
  (-fix-z0-and-broadcast (:to (to -param-fn)) data z0))

(defn convert [input]
  "Converts a [[mathjs/matrix]] having dimensions n-freqs x n-ports x n-ports  
   containing [[mathjs/complex]] numbers representing the response of a network
   from one parameter representation to another.
   Arguments:
   - `input`, a map with the following keys:
       - `:data` : [[mathjs/matrix]] having dimensions n-freqs x n-ports x n-ports  
                     containing [[mathjs/complex]].
       - `:from` : The current parameters represented by `(:data input)`.
       - `:to`   : The desired parameter representation of the network
                     represented by `(:data input)` to be returned.
       - `:z0`   : A vector with n-ports `(mathjs/complex)` entries, or
                     a single scalar representing the impedance of all ports.   
   "
  (if (= (:from input) (:to input))
    (:data input)
    (from-s (assoc input :data (to-s input)))))

(defn renormalize
  "Takes `s`, a [[mathjs/matrix]] having dimensions n-freqs x n-ports x n-ports 
   containing s-parameters referenced to `z0-current`, and returns a NxMxM matrix of 
   s-parameters referenced to `z0-desired`."
  [s z0-current z0-desired]
  (to-s {:from :z :data (from-s {:to :z :data s :z0 z0-current}) :z0 z0-desired}))
