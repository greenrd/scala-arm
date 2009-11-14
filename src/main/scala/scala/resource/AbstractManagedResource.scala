// -----------------------------------------------------------------------------
//
//  scala.arm - The Scala Incubator Project
//  Copyright (c) 2009 The Scala Incubator Project. All rights reserved.
//
//  The primary distribution site is http://jsuereth.github.com/scala-arm
//
//  This software is released under the terms of the Revised BSD License.
//  There is NO WARRANTY.  See the file LICENSE for the full text.
//
// -----------------------------------------------------------------------------


package scala.resource

import scala.collection.Traversable
import scala.collection.Seq
import scala.collection.Iterator
import scala.util.control.Exception


/**
 * An implementation of an ExtractableManagedResource that defers all processing until the user pulls out information using
 * either or opt functions.
 */
private[resource] class DeferredExtractableManagedResource[+A,R](val resource : ManagedResource[R], val translate : R => A) extends 
  ExtractableManagedResource[A] with ManagedResourceOperations[A] { self =>

  override def acquireFor[B](f : A => B) : Either[List[Throwable], B] = resource acquireFor translate.andThen(f)

  override def either = resource acquireFor translate

  override def opt = either.right.toOption

  override def equals(that : Any) = that match {
    case x : DeferredExtractableManagedResource[A,R] => (x.resource == resource) && (x.translate == translate)
    case _ => false
  }
  override def hashCode() : Int = (resource.hashCode << 7) + translate.hashCode + 13

  override def toString = "DeferredExtractableManagedResource(" + resource + ", " + translate + ")"
}


/**
 * Abstract class implementing most of the managed resource features.
 */
trait AbstractManagedResource[+R,H] extends ManagedResource[R] with ManagedResourceOperations[R] { self =>

  /** 
   * Opens a given resource, returning a handle to execute against during the "session" of the resource being open.
   */
  protected def open : H

  /**
   * Closes a resource using the handle.  This method will throw any exceptions normally occuring during the close of
   * a resource.
   */
  protected def unsafeClose(handle : H) : Unit

  /** 
   * Returns the resource that we are managing.
   */
  protected def translate(handle : H) : R
  /**
   * The list of exceptions that get caught during ARM and will not prevent a call to close.
   */
  protected def caughtException : Seq[Class[_]] = List(classOf[Throwable])

  override def acquireFor[B](f : R => B) : Either[List[Throwable], B] = {
    import Exception._
    val handle = open
    val result  = catching(caughtException : _*) either (f(translate(handle)))
    val close = catching(caughtException : _*) either unsafeClose(handle)
    //Combine resulting exceptions as necessary     
    result.left.map[List[Throwable]]( _ :: close.left.toOption.toList)
  }
}



/**
 * This class is used when a resource is its own handle.
 */
trait AbstractUntranslatedManagedResource[R] extends AbstractManagedResource[R,R] { self =>
  override protected def translate(handle : R) : R = handle
}