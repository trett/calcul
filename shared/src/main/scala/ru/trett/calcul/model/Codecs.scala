package ru.trett.calcul.model

import upickle.default.*
import java.time.{Instant, LocalDate}

object Codecs:
  given ReadWriter[Instant]   = readwriter[String].bimap[Instant](_.toString, Instant.parse)
  given ReadWriter[LocalDate] = readwriter[String].bimap[LocalDate](_.toString, LocalDate.parse)
