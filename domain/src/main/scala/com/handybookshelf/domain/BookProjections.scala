package com.handybookshelf
package domain

import cats.effect.IO
import com.handybookshelf.util.{ISBN, Timestamp}

// Query側のビューモデル
final case class BookView(
    id: BookId,
    title: NES,
    isbn: Option[ISBN],
    location: Option[Location],
    tags: Set[Tag],
    devices: Set[Device],
    isDeleted: Boolean,
    lastModified: Timestamp
)

final case class BookSummary(
    id: BookId,
    title: NES,
    location: Option[Location],
    tagCount: Int,
    deviceCount: Int
)

final case class LocationView(
    location: Location,
    books: List[BookSummary]
)

final case class TagView(
    tag: Tag,
    books: List[BookSummary]
)

// Projection基底trait
trait Projection[V]:
  def apply(event: DomainEvent): IO[Unit]
  def getView(id: String): IO[Option[V]]
  def getAllViews(): IO[List[V]]

// Book詳細ビューProjection
class BookViewProjection extends Projection[BookView]:
  private var views: Map[String, BookView] = Map.empty

  def apply(event: DomainEvent): IO[Unit] = event match
    case event: BookEvent => handleBookEvent(event)
    case _                => IO.unit

  def getView(id: String): IO[Option[BookView]] =
    IO.pure(views.get(id))

  def getAllViews(): IO[List[BookView]] =
    IO.pure(views.values.toList.filterNot(_.isDeleted))

  private def handleBookEvent(event: BookEvent): IO[Unit] =
    val bookId = event.bookId.toString
    val updatedView = views.get(bookId) match
      case Some(view) => updateView(view, event)
      case None       => createViewFromEvent(event)

    IO {
      views = views.updated(bookId, updatedView)
    }

  private def createViewFromEvent(event: BookEvent): BookView = event match
    case BookRegistered(_, bookId, isbn, title, _, timestamp) =>
      BookView(
        id = bookId,
        title = title,
        isbn = isbn,
        location = None,
        tags = Set.empty,
        devices = Set.empty,
        isDeleted = false,
        lastModified = timestamp
      )
    case _ => throw new IllegalStateException(s"Cannot create view from non-registration event: ${event.eventType}")

  private def updateView(view: BookView, event: BookEvent): BookView = event match
    case BookLocationChanged(_, _, _, newLocation, _, timestamp) =>
      view.copy(location = Some(newLocation), lastModified = timestamp)

    case BookTagAdded(_, _, tag, _, timestamp) =>
      view.copy(tags = view.tags + tag, lastModified = timestamp)

    case BookTagRemoved(_, _, tag, _, timestamp) =>
      view.copy(tags = view.tags - tag, lastModified = timestamp)

    case BookDeviceAdded(_, _, device, _, timestamp) =>
      view.copy(devices = view.devices + device, lastModified = timestamp)

    case BookDeviceRemoved(_, _, device, _, timestamp) =>
      view.copy(devices = view.devices - device, lastModified = timestamp)

    case BookTitleUpdated(_, _, _, newTitle, _, timestamp) =>
      view.copy(title = newTitle, lastModified = timestamp)

    case BookRemoved(_, _, _, timestamp) =>
      view.copy(isDeleted = true, lastModified = timestamp)

    case _ => view

// Location別ビューProjection
class LocationViewProjection(bookViewProjection: BookViewProjection) extends Projection[LocationView]:

  def apply(event: DomainEvent): IO[Unit] = event match
    case _: BookLocationChanged => IO.unit // BookViewProjectionが先に更新されるので、特に処理不要
    case _                      => IO.unit

  def getView(locationName: String): IO[Option[LocationView]] =
    for {
      allBooks <- bookViewProjection.getAllViews()
      booksAtLocation = allBooks
        .filter(_.location.exists(loc => loc.name.toString == locationName))
        .map(view =>
          BookSummary(
            id = view.id,
            title = view.title,
            location = view.location,
            tagCount = view.tags.size,
            deviceCount = view.devices.size
          )
        )
    } yield {
      if (booksAtLocation.nonEmpty)
        Some(LocationView(Location(locationName.nes), booksAtLocation))
      else
        None
    }

  def getAllViews(): IO[List[LocationView]] =
    for {
      allBooks <- bookViewProjection.getAllViews()
      locationGroups = allBooks
        .flatMap(view => view.location.map(loc => (loc, view)))
        .groupBy(_._1)
        .map { case (location, books) =>
          LocationView(
            location = location,
            books = books
              .map(_._2)
              .map(view =>
                BookSummary(
                  id = view.id,
                  title = view.title,
                  location = view.location,
                  tagCount = view.tags.size,
                  deviceCount = view.devices.size
                )
              )
              .toList
          )
        }
        .toList
    } yield locationGroups

// Tag別ビューProjection
class TagViewProjection(bookViewProjection: BookViewProjection) extends Projection[TagView]:

  def apply(event: DomainEvent): IO[Unit] = event match
    case _: BookTagAdded | _: BookTagRemoved => IO.unit
    case _                                   => IO.unit

  def getView(tagName: String): IO[Option[TagView]] =
    for {
      allBooks <- bookViewProjection.getAllViews()
      booksWithTag = allBooks
        .filter(_.tags.exists(_.name == tagName))
        .map(view =>
          BookSummary(
            id = view.id,
            title = view.title,
            location = view.location,
            tagCount = view.tags.size,
            deviceCount = view.devices.size
          )
        )
    } yield {
      if (booksWithTag.nonEmpty)
        Some(TagView(Tag(tagName), booksWithTag))
      else
        None
    }

  def getAllViews(): IO[List[TagView]] =
    for {
      allBooks <- bookViewProjection.getAllViews()
      tagGroups = allBooks
        .flatMap(view => view.tags.map(tag => (tag, view)))
        .groupBy(_._1)
        .map { case (tag, books) =>
          TagView(
            tag = tag,
            books = books
              .map(_._2)
              .map(view =>
                BookSummary(
                  id = view.id,
                  title = view.title,
                  location = view.location,
                  tagCount = view.tags.size,
                  deviceCount = view.devices.size
                )
              )
              .toList
          )
        }
        .toList
    } yield tagGroups

// Projectionの管理
class ProjectionManager(
    val bookViewProjection: BookViewProjection,
    val locationViewProjection: LocationViewProjection,
    val tagViewProjection: TagViewProjection
):
  def handleEvent(event: DomainEvent): IO[Unit] =
    for {
      _ <- bookViewProjection.apply(event)
      _ <- locationViewProjection.apply(event)
      _ <- tagViewProjection.apply(event)
    } yield ()

object ProjectionManager:
  def create(): ProjectionManager =
    val bookViewProjection     = new BookViewProjection()
    val locationViewProjection = new LocationViewProjection(bookViewProjection)
    val tagViewProjection      = new TagViewProjection(bookViewProjection)

    new ProjectionManager(bookViewProjection, locationViewProjection, tagViewProjection)
