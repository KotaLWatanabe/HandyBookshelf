package com.handybookshelf
package controller.api

import sttp.tapir.*

package object endpoints {
  val endpointRoot: Endpoint[Unit, Unit, Unit, Unit, Any] =
    endpoint.in("api").in("v1")
}
