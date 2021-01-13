package com.dataintuitive.viash.platforms

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

package object docker {
  import com.dataintuitive.viash.helpers.Circe._

  // encoder and decoder for direction
  implicit val encodeResolveVolume: Encoder[DockerResolveVolume] = Encoder.instance {
    v => Json.fromString(v.toString)
  }
  implicit val decodeResolveVolume: Decoder[DockerResolveVolume] = Decoder.instance {
    cursor =>
      cursor.value.as[String].map(s =>
        s.toLowerCase() match {
          case "manual" => Manual
          case "auto" | "automatic" => Automatic
        }
      )
  }

  // encoder and decoder for direction
  implicit val encodeSetupStrategy: Encoder[DockerSetupStrategy] = Encoder.instance {
    v => Json.fromString(v.toString)
  }
  implicit val decodeSetupStrategy: Decoder[DockerSetupStrategy] = Decoder.instance {
    cursor =>
      cursor.value.as[String].map(s =>
        s.toLowerCase().replaceAll("_", "") match {
          case "alwaysbuild" => AlwaysBuild
          case "alwayspull" => AlwaysPull
          case "alwayspullelsebuild" => AlwaysPullElseBuild
          case "ifneedbebuild" | "build" => IfNeedBeBuild
          case "ifneedbepull" | "pull" => IfNeedBePull
          case "ifneedbepullelsebuild" | "pullelsebuild" => IfNeedBePullElseBuild
        }
      )
  }
}
