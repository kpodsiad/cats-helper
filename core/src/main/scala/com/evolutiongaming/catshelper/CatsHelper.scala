package com.evolutiongaming.catshelper

import cats.effect.concurrent.Deferred
import cats.effect.implicits._
import cats.effect.{Concurrent, Fiber, Resource}
import cats.implicits._
import cats.{ApplicativeError, MonadError}

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.{Either, Try}

object CatsHelper {

  class ApplicativeErrorOpsCatsHelper[F[_], E](val self: ApplicativeError[F, E]) extends AnyVal {

    def redeem[A, B](fa: F[A])(recover: E => B, ab: A => B): F[B] = {
      val fb = self.map(fa)(ab)
      self.handleError(fb)(recover)
    }
  }


  implicit class MonadErrorOpsCatsHelper[F[_], E](val self: MonadError[F, E]) extends AnyVal {

    def redeemWith[A, B](fa: F[A])(recover: E => F[B], ab: A => F[B]): F[B] = {
      val fb = self.flatMap(fa)(ab)
      self.handleErrorWith(fb)(recover)
    }
  }


  implicit class ConcurrentOpsCatsHelper[F[_]](val self: Concurrent[F]) extends AnyVal {

    def startEnsure[A](fa: F[A]): F[Fiber[F, A]] = {
      implicit val F = self

      def faOf(started: Deferred[F, Unit]) = {
        for {
          _ <- started.complete(())
          a <- fa
        } yield a
      }

      for {
        started <- Deferred[F, Unit]
        fiber   <- faOf(started).start
        _       <- started.get
      } yield fiber
    }
  }


  implicit class IdOpsCatsHelper[A](val self: A) extends AnyVal {

    def castM[F[_]: MonadThrowable, B <: A](implicit tag: ClassTag[B]): F[B] = {

      def error = new ClassCastException(s"${ self.getClass.getName } cannot be cast to ${ tag.runtimeClass.getName }")

      castOpt[B].fold { error.raiseError[F, B] } { _.pure[F] }
    }


    def castOpt[B <: A](implicit tag: ClassTag[B]): Option[B] = tag.unapply(self)
  }


  class OpsCatsHelper[F[_], A](val self: F[A]) extends AnyVal {

    def redeem[B, E](recover: E => B, ab: A => B)(implicit F: ApplicativeError[F, E]): F[B] = {
      F.redeem(self)(recover, ab)
    }

    def redeemWith[B, E](recover: E => F[B], ab: A => F[B])(implicit F: MonadError[F, E]): F[B] = {
      F.redeemWith(self)(recover, ab)
    }


    def startEnsure(implicit F: Concurrent[F]): F[Fiber[F, A]] = F.startEnsure(self)


    def toTry(implicit F: ToTry[F]): Try[A] = F.apply(self)


    def toFuture(implicit F: ToFuture[F]): Future[A] = F.apply(self)
  }


  implicit class Ops1CatsHelper[F[_], A](val self: F[A]) extends AnyVal {


    def startEnsure(implicit F: Concurrent[F]): F[Fiber[F, A]] = F.startEnsure(self)


    def toTry(implicit F: ToTry[F]): Try[A] = F.apply(self)


    def toFuture(implicit F: ToFuture[F]): Future[A] = F.apply(self)
  }


  implicit class TryOpsCatsHelper[A](val self: Try[A]) extends AnyVal {

    def fromTry[F[_]](implicit fromTry: FromTry[F]): F[A] = fromTry(self)
  }


  implicit class ResourceOpsCatsHelper[F[_], A](val self: Resource[F, A]) extends AnyVal {

    def fenced(implicit F: Concurrent[F]): Resource[F, A] = ResourceFenced(self)
  }


  implicit class BooleanOpsCatsHelper(val self: Boolean) extends AnyVal {

    def trueOr[A](a: => A): Either[A, Unit] = {
      if (self) ().asRight else a.asLeft
    }

    def falseOr[A](a: => A): Either[A, Unit] = {
      if (self) a.asLeft else ().asRight
    }
  }
}
