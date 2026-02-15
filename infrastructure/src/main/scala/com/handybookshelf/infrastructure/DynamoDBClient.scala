package com.handybookshelf.infrastructure

import cats.effect.{IO, Resource}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient as AwsDynamoDbClient
import java.net.URI

object DynamoDBClient {

  def createLocalClient(): Resource[IO, AwsDynamoDbClient] =
    Resource.make(
      IO.delay {
        AwsDynamoDbClient
          .builder()
          .endpointOverride(URI.create("http://localhost:8000"))
          .region(Region.US_EAST_1)
          .credentialsProvider(
            StaticCredentialsProvider.create(
              AwsBasicCredentials.create("dummy", "dummy")
            )
          )
          .build()
      }
    )(client => IO.delay(client.close()))

  def createClient(endpoint: URI, region: Region = Region.US_EAST_1): Resource[IO, AwsDynamoDbClient] =
    Resource.make(
      IO.delay {
        val builder = AwsDynamoDbClient
          .builder()
          .region(region)

        // For local development, override endpoint and use dummy credentials
        val _ = if (endpoint.getHost == "localhost") {
          builder
            .endpointOverride(endpoint)
            .credentialsProvider(
              StaticCredentialsProvider.create(
                AwsBasicCredentials.create("dummy", "dummy")
              )
            )
        } else builder

        builder.build()
      }
    )(client => IO.delay(client.close()))
}
