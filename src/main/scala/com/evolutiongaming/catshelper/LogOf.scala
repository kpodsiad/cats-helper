package com.evolutiongaming.catshelper

import cats.effect.Sync
import cats.implicits._
import cats.{Applicative, FlatMap, ~>}
import org.slf4j.{ILoggerFactory, LoggerFactory}

trait LogOf[F[_]] {

  def apply(source: String): F[Log[F]]

  def apply(source: Class[_]): F[Log[F]]
}

object LogOf {

  def apply[F[_]](implicit F: LogOf[F]): LogOf[F] = F


  def apply[F[_] : Sync](factory: ILoggerFactory): LogOf[F] = new LogOf[F] {

    def apply(source: String) = {
      for {
        log <- Sync[F].delay { factory.getLogger(source) }
      } yield {
        Log[F](log)
      }
    }
    def apply(source: Class[_]) = apply(source.getName.stripSuffix("$"))
  }


  def slfj4[F[_] : Sync]: F[LogOf[F]] = {
    for {
      factory <- Sync[F].delay { LoggerFactory.getILoggerFactory }
    } yield {
      apply(factory)
    }
  }


  def empty[F[_] : Applicative]: LogOf[F] = const(Log.empty[F].pure[F])


  def const[F[_]](log: F[Log[F]]): LogOf[F] = new LogOf[F] {

    def apply(source: String) = log

    def apply(source: Class[_]) = log
  }


  implicit class LogOfOps[F[_]](val self: LogOf[F]) extends AnyVal {

    def mapK[G[_] : FlatMap](f: F ~> G): LogOf[G] = new LogOf[G] {

      def apply(source: String) = {
        for {
          log <- f(self(source))
        } yield {
          log.mapK(f)
        }
      }

      def apply(source: Class[_]) = {
        for {
          log <- f(self(source))
        } yield {
          log.mapK(f)
        }
      }
    }
  }
}