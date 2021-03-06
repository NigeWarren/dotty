/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2013, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

package scala
package collection

import generic._
import Seq.fill

/** A template trait for non-strict views of sequences.
 *  $seqViewInfo
 *
 *  @define seqViewInfo
 *  $viewInfo
 *  All views for sequences are defined by re-interpreting the `length` and
 * `apply` methods.
 *
 *  @author Martin Odersky
 *  @version 2.8
 *  @since   2.8
 *  @tparam A    the element type of the view
 *  @tparam Coll the type of the underlying collection containing the elements.
 *  @tparam This the type of the view itself
 */
trait SeqViewLike[+A,
                  +Coll,
                  +This <: SeqView[A, Coll] with SeqViewLike[A, Coll, This]]
  extends Seq[A] with SeqLike[A, This] with IterableView[A, Coll] with IterableViewLike[A, Coll, This]
{ self =>

  /** Explicit instantiation of the `Transformed` trait to reduce class file size in subclasses. */
  private[collection] abstract class AbstractTransformedS[+B] extends Seq[B] with super[IterableViewLike].TransformedI[B] with TransformedS[B]

  trait TransformedS[+B] extends SeqView[B, Coll] with super.TransformedI[B] {
    def length: Int
    def apply(idx: Int): B
    override def toString = viewToString
  }

  trait EmptyViewS extends TransformedS[Nothing] with super.EmptyViewI {
    final override def length = 0
    final override def apply(n: Int) = Nil(n)
  }

  trait ForcedS[B] extends super.ForcedI[B] with TransformedS[B] {
    def length = forced.length
    def apply(idx: Int) = forced.apply(idx)
  }

  trait SlicedS extends super.SlicedI with TransformedS[A] {
    def length = iterator.size
    def apply(idx: Int): A =
      if (idx >= 0 && idx + from < until) self.apply(idx + from)
      else throw new IndexOutOfBoundsException(idx.toString)

    override def foreach[U](f: A => U) = iterator foreach f
    override def iterator: Iterator[A] = self.iterator drop from take endpoints.width
  }

  trait MappedS[B] extends super.MappedI[B] with TransformedS[B] {
    def length = self.length
    def apply(idx: Int): B = mapping(self(idx))
  }

  trait FlatMappedS[B] extends super.FlatMappedI[B] with TransformedS[B] {
    protected[this] lazy val index = {
      val index = new Array[Int](self.length + 1)
      index(0) = 0
      for (i <- 0 until self.length) // note that if the mapping returns a list, performance is bad, bad
        index(i + 1) = index(i) + mapping(self(i)).seq.size
      index
    }
    protected[this] def findRow(idx: Int, lo: Int, hi: Int): Int = {
      val mid = (lo + hi) / 2
      if (idx < index(mid)) findRow(idx, lo, mid - 1)
      else if (idx >= index(mid + 1)) findRow(idx, mid + 1, hi)
      else mid
    }
    def length = index(self.length)
    def apply(idx: Int) = {
      if (idx < 0 || idx >= length) throw new IndexOutOfBoundsException(idx.toString)
      val row = findRow(idx, 0, self.length - 1)
      mapping(self(row)).seq.toSeq(idx - index(row))
    }
  }

  trait AppendedS[B >: A] extends super.AppendedI[B] with TransformedS[B] {
    protected[this] lazy val restSeq = rest.toSeq
    def length = self.length + restSeq.length
    def apply(idx: Int) =
      if (idx < self.length) self(idx) else restSeq(idx - self.length)
  }

  trait PrependedS[B >: A] extends super.PrependedI[B] with TransformedS[B] {
    protected[this] lazy val fstSeq = fst.toSeq
    def length: Int = fstSeq.length + self.length
    def apply(idx: Int): B =
      if (idx < fstSeq.length) fstSeq(idx)
      else self.apply(idx - fstSeq.length)
  }

  trait FilteredS extends super.FilteredI with TransformedS[A] {
    protected[this] lazy val index = {
      var len = 0
      val arr = new Array[Int](self.length)
      for (i <- 0 until self.length)
        if (pred(self(i))) {
          arr(len) = i
          len += 1
        }
      arr take len
    }
    def length = index.length
    def apply(idx: Int) = self(index(idx))
  }

  trait TakenWhileS extends super.TakenWhileI with TransformedS[A] {
    protected[this] lazy val len = self prefixLength pred
    def length = len
    def apply(idx: Int) =
      if (idx < len) self(idx)
      else throw new IndexOutOfBoundsException(idx.toString)
  }

  trait DroppedWhileS extends super.DroppedWhileI with TransformedS[A] {
    protected[this] lazy val start = self prefixLength pred
    def length = self.length - start
    def apply(idx: Int) =
      if (idx >= 0) self(idx + start)
      else throw new IndexOutOfBoundsException(idx.toString)
  }

  trait ZippedS[B] extends super.ZippedI[B] with TransformedS[(A, B)] {
    protected[this] lazy val thatSeq = other.seq.toSeq
    /* Have to be careful here - other may be an infinite sequence. */
    def length = if ((thatSeq lengthCompare self.length) <= 0) thatSeq.length else self.length
    def apply(idx: Int) = (self.apply(idx), thatSeq.apply(idx))
  }

  trait ZippedAllS[A1 >: A, B] extends super.ZippedAllI[A1, B] with TransformedS[(A1, B)] {
    protected[this] lazy val thatSeq = other.seq.toSeq
    def length: Int = self.length max thatSeq.length
    def apply(idx: Int) =
      (if (idx < self.length) self.apply(idx) else thisElem,
       if (idx < thatSeq.length) thatSeq.apply(idx) else thatElem)
  }

  trait ReversedS extends TransformedS[A] {
    override def iterator: Iterator[A] = createReversedIterator
    def length: Int = self.length
    def apply(idx: Int): A = self.apply(length - 1 - idx)
    final override protected[this] def viewIdentifier = "R"

    private def createReversedIterator = {
      var lst = List[A]()
      for (elem <- self) lst ::= elem
      lst.iterator
    }
  }

  // Note--for this to work, must ensure 0 <= from and 0 <= replaced
  // Must also take care to allow patching inside an infinite stream
  // (patching in an infinite stream is not okay)
  trait PatchedS[B >: A] extends TransformedS[B] {
    protected[this] lazy val from: Int
    protected[this] lazy val patch: GenSeq[B]
    protected[this] lazy val replaced: Int
    private lazy val plen = patch.length
    override def iterator: Iterator[B] = self.iterator patch (from, patch.iterator, replaced)
    def length: Int = {
      val len = self.length
      val pre = math.min(from, len)
      val post = math.max(0, len - pre - replaced)
      pre + plen + post
    }
    def apply(idx: Int): B = {
      val actualFrom = if (self.lengthCompare(from) < 0) self.length else from
      if (idx < actualFrom) self.apply(idx)
      else if (idx < actualFrom + plen) patch.apply(idx - actualFrom)
      else self.apply(idx - plen + replaced)
    }
    final override protected[this] def viewIdentifier = "P"
  }

  /** Boilerplate method, to override in each subclass
   *  This method could be eliminated if Scala had virtual classes
   */
  protected override def newForced[B](xs: => GenSeq[B]): TransformedS[B] = new AbstractTransformedS[B] with ForcedS[B] { lazy val forced = xs }
  protected override def newAppended[B >: A](that: GenTraversable[B]): TransformedS[B] = new AbstractTransformedS[B] with AppendedS[B] { lazy val rest = that }
  protected override def newPrepended[B >: A](that: GenTraversable[B]): TransformedS[B] = new AbstractTransformedS[B] with PrependedS[B] { lazy protected[this] val fst = that }
  protected override def newMapped[B](f: A => B): TransformedS[B] = new AbstractTransformedS[B] with MappedS[B] { lazy val mapping = f }
  protected override def newFlatMapped[B](f: A => GenTraversableOnce[B]): TransformedS[B] = new AbstractTransformedS[B] with FlatMappedS[B] { lazy val mapping = f }
  protected override def newFiltered(p: A => Boolean): TransformedS[A] = new AbstractTransformedS[A] with FilteredS { lazy val pred = p }
  protected override def newSliced(_endpoints: SliceInterval): TransformedS[A] = new AbstractTransformedS[A] with SlicedS { lazy val endpoints = _endpoints }
  protected override def newDroppedWhile(p: A => Boolean): TransformedS[A] = new AbstractTransformedS[A] with DroppedWhileS { lazy val pred = p }
  protected override def newTakenWhile(p: A => Boolean): TransformedS[A] = new AbstractTransformedS[A] with TakenWhileS { lazy val pred = p }
  protected override def newZipped[B](that: GenIterable[B]): TransformedS[(A, B)] = new AbstractTransformedS[(A, B)] with ZippedS[B] { lazy val other = that }
  protected override def newZippedAll[A1 >: A, B](that: GenIterable[B], _thisElem: A1, _thatElem: B): TransformedS[(A1, B)] = new AbstractTransformedS[(A1, B)] with ZippedAllS[A1, B] {
    lazy val other = that
    lazy val thisElem = _thisElem
    lazy val thatElem = _thatElem
  }
  protected def newReversed: TransformedS[A] = new AbstractTransformedS[A] with ReversedS
  protected def newPatched[B >: A](_from: Int, _patch: GenSeq[B], _replaced: Int): TransformedS[B] = new AbstractTransformedS[B] with PatchedS[B] {
    lazy val from = _from
    lazy val patch = _patch
    lazy val replaced = _replaced
  }

  // see comment in IterableViewLike.
  protected override def newTaken(n: Int): TransformedS[A] = newSliced(SliceInterval(0, n))
  protected override def newDropped(n: Int): TransformedS[A] = newSliced(SliceInterval(n, Int.MaxValue))

  override def reverse: This = newReversed.asInstanceOf[This]

  override def patch[B >: A, That](from: Int, patch: GenSeq[B], replaced: Int)(implicit bf: CanBuildFrom[This, B, That]): That = {
    // Be careful to not evaluate the entire sequence!  Patch should work (slowly, perhaps) on infinite streams.
    val nonNegFrom = math.max(0,from)
    val nonNegRep = math.max(0,replaced)
    newPatched(nonNegFrom, patch, nonNegRep).asInstanceOf[That]
// was:    val b = bf(repr)
//    if (b.isInstanceOf[NoBuilder[_]]) newPatched(from, patch, replaced).asInstanceOf[That]
//    else super.patch[B, That](from, patch, replaced)(bf)
  }

  override def padTo[B >: A, That](len: Int, elem: B)(implicit bf: CanBuildFrom[This, B, That]): That =
    patch(length, fill(len - length)(elem), 0)

  override def reverseMap[B, That](f: A => B)(implicit bf: CanBuildFrom[This, B, That]): That =
    reverse map f

  override def updated[B >: A, That](index: Int, elem: B)(implicit bf: CanBuildFrom[This, B, That]): That = {
    require(0 <= index && index < length) // !!! can't call length like this.
    patch(index, List(elem), 1)(bf)
  }

  override def +:[B >: A, That](elem: B)(implicit bf: CanBuildFrom[This, B, That]): That =
    newPrepended(elem :: Nil).asInstanceOf[That]

  override def :+[B >: A, That](elem: B)(implicit bf: CanBuildFrom[This, B, That]): That =
    ++(Iterator.single(elem))(bf)

  override def union[B >: A, That](that: GenSeq[B])(implicit bf: CanBuildFrom[This, B, That]): That =
    newForced(thisSeq union that).asInstanceOf[That]

  override def diff[B >: A](that: GenSeq[B]): This =
    newForced(thisSeq diff that).asInstanceOf[This]

  override def intersect[B >: A](that: GenSeq[B]): This =
    newForced(thisSeq intersect that).asInstanceOf[This]

  override def sorted[B >: A](implicit ord: Ordering[B]): This =
    newForced(thisSeq sorted ord).asInstanceOf[This]

  override def sortWith(lt: (A, A) => Boolean): This =
    newForced(thisSeq sortWith lt).asInstanceOf[This]

  override def sortBy[B](f: (A) => B)(implicit ord: Ordering[B]): This =
    newForced(thisSeq sortBy f).asInstanceOf[This]

  override def combinations(n: Int): Iterator[This] =
    (thisSeq combinations n).map(as => newForced(as).asInstanceOf[This])

  override def permutations: Iterator[This] =
    thisSeq.permutations.map(as => newForced(as).asInstanceOf[This])

  override def distinct: This =
    newForced(thisSeq.distinct).asInstanceOf[This]

  override def stringPrefix = "SeqView"
}
