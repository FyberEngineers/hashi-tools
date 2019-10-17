package com.fyber.vault

/**
  * Created by dani on 29/10/18.
  */
package object configuration {
  implicit def toOption[T <: AnyRef](field: T): Option[T] = Option(field)

}
