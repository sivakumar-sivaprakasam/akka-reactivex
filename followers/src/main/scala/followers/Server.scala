package followers

import akka.NotUsed
import akka.event.Logging
import akka.stream.scaladsl.{BroadcastHub, Flow, Framing, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorAttributes, Materializer, OverflowStrategy}
import akka.util.ByteString
import followers.model.Event.Follow
import followers.model.{Event, Followers, Identity}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.runtime.Nothing$
import scala.util.Try

/**
  * Utility object that describe stream manipulations used by the server
  * implementation.
  */
object Server {

  /**
    * A flow that consumes chunks of bytes and produces `String` messages.
    *
    * Each incoming chunk of bytes doesn’t necessarily contain ''exactly one'' message
    * payload (it can contain fragments of payloads only). You have to process these
    * chunks to produce ''frames'' containing exactly one message payload.
    *
    * Messages are delimited by the '\n' character.
    *
    * If the last frame does not end with a delimiter, this flow should fail the
    * stream instead of returning a truncated frame.
    *
    * Hint: you may find the [[Framing]] flows useful.
    */
  val reframedFlow: Flow[ByteString, String, NotUsed] =
    Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = false).map(_.utf8String)

  /**
    * A flow that consumes chunks of bytes and produces [[Event]] messages.
    *
    * Each incoming chunk of bytes doesn't necessarily contain exactly one message payload (it
    * can contain fragments of payloads only). You have to process these chunks to produce
    * frames containing exactly one message payload before you can parse such messages with
    * [[Event.parse]].
    *
    * Hint: reuse `reframedFlow`
    */
  val eventParserFlow: Flow[ByteString, Event, NotUsed] =
    reframedFlow.map(Event.parse(_))

  /**
    * Implement a Sink that will look for the first [[Identity]]
    * (we expect there will be only one), from a stream of commands and materializes a Future with it.
    *
    * Subsequent values once we found an identity can be ignored.
    *
    * Note that you may need the Sink's materialized value; you may
    * want to compare the signatures of `Flow.to` and `Flow.toMat`
    * (and have a look at `Keep.right`).
    */
  val identityParserSink: Sink[ByteString, Future[Identity]] = {
    val flow: Flow[ByteString, Identity, NotUsed] = reframedFlow.map(Identity.parse(_))
    val sink: Sink[Identity, Future[Identity]] = Sink.head[Identity]
    flow.toMat(sink)(Keep.right)
  }

  /**
    * A flow that consumes unordered messages and produces messages ordered by `sequenceNr`.
    *
    * User clients expect to be notified of events in the correct order, regardless of the order in which the
    * event source sent them.
    *
    * You will have to buffer messages with a higher sequence number than the next one
    * expected to be produced. The first message to produce has a sequence number of 1.
    *
    * You may want to use `statefulMapConcat` in order to keep the state needed for this
    * operation around in the operator.
    */
    val reintroduceOrdering: Flow[Event, Event, NotUsed] = {
      Flow[Event].statefulMapConcat { () =>
        var expectedSeq = 1
        val pendingFlows = scala.collection.mutable.Set[Event]()
        element => {
          pendingFlows.add(element)
          var returnFlows: ListBuffer[Event] = ListBuffer.empty[Event]
          pendingFlows.toList.sortBy(_.sequenceNr).foreach(x => {
            if (x.sequenceNr == expectedSeq) {
              returnFlows += x
              expectedSeq += 1
            }
          })
          returnFlows.toList
        }
      }
    }

  /**
    * A flow that associates a state of [[Followers]] to
    * each incoming [[Event]].
    *
    * Hints:
    *  - start with a state where nobody follows nobody,
    *  - you may find the `statefulMapConcat` operation useful.
    */
  val followersFlow: Flow[Event, (Event, Followers), NotUsed] = {
    Flow[Event].statefulMapConcat { () =>
      var followersMap: Map[Int, Set[Int]] = Map.empty[Int, Set[Int]]
       eventValue => eventValue match {
          case f:Event.Follow => {
            if (followersMap.contains(f.fromUserId)) {
              //followersMap.get(f.fromUserId).get + f.toUserId
              List((eventValue, Map(f.fromUserId -> (followersMap.get(f.fromUserId).get + f.toUserId))))
            } else {
              followersMap = followersMap + (f.fromUserId -> Set(f.toUserId))
              List((eventValue, Map(f.fromUserId -> Set(f.toUserId))))
            }
          }
          case u:Event.Unfollow => {
            var localSet: Set[Int] = Set.empty[Int]
            followersMap.get(u.fromUserId) match {
              case Some(f) => localSet = f - u.toUserId
              case None =>
            }
            followersMap = followersMap - u.fromUserId
            followersMap = followersMap + (u.fromUserId -> localSet)
            List((eventValue, Map(u.fromUserId -> localSet)))
          }
          case s:Event.StatusUpdate => {
            var localMap: Map[Int, Set[Int]] = Map.empty[Int, Set[Int]]
            followersMap.foreach {
              x => if (x._2.contains(s.fromUserId)) {
                localMap = localMap + x
              }
            }
            List((eventValue, localMap))
          }
          case _ => {
            List((eventValue, Map.empty[Int, Set[Int]]))
          }
        }
      }
  }

  /**
    * @return Whether the given user should be notified by the incoming `Event`,
    *         given the current state of `Followers`. See [[Event]] for more
    *         information of when users should be notified about them.
    * @param userId Id of the user
    * @param eventAndFollowers Event and current state of followers
    */
  def isNotified(userId: Int)(eventAndFollowers: (Event, Followers)): Boolean = {
    eventAndFollowers._1 match {
      case _:Event.Broadcast =>
        true
      case s:Event.StatusUpdate => {
        val notifyUsersSet: Option[Set[Int]] = eventAndFollowers._2.get(userId)
        notifyUsersSet match {
          case Some(f) => f.contains(s.fromUserId)
          case None => false
        }
      }
      case f:Event.Follow => {
        if (f.toUserId == userId) {
          true
        } else {
          false
        }
      }
      case _ =>
        false
    }
  }

  // Utilities to temporarily have unimplemented parts of the program
//  private def unimplementedFlow[A, B, C]: Flow[A, B, C] =
//    Flow.fromFunction[A, B](_ => ???).mapMaterializedValue(_ => ??? : C)
//
//  private def unimplementedSink[A, B]: Sink[A, B] = Sink.ignore.mapMaterializedValue(_ => ??? : B)
}

/**
  * Creates a hub accepting several client connections and a single event connection.
  *
  * @param executionContext Execution context for `Future` values transformations
  * @param materializer Stream materializer
  */
class Server()(implicit executionContext: ExecutionContext, materializer: Materializer)
  extends ExtraStreamOps {
  import Server._

  /**
    * The hub is instantiated here. It allows new user clients to join afterwards
    * and receive notifications from their event feed (see the `clientFlow()` member
    * below). The hub also allows new events to be pushed to it (see the `eventsFlow`
    * member below).
    *
    * The following expression creates the hub and returns a pair containing:
    *  1. A `Sink` that consumes events data,
    *  2. and a `Source` of decoded events paired with the state of followers.
    */
  val (inboundSink, broadcastOut) = {
    /**
      * A flow that consumes the event source, re-frames it,
      * decodes the events, re-orders them, and builds a Map of
      * followers at each point it time. It produces a stream
      * of the decoded events associated with the current state
      * of the followers Map.
      */
    val incomingDataFlow: Flow[ByteString, (Event, Followers), NotUsed] = {
      eventParserFlow.via(reintroduceOrdering).via(followersFlow)
    }


    // Wires the MergeHub and the BroadcastHub together and runs the graph
    MergeHub.source[ByteString](256)
      .via(incomingDataFlow)
      .toMat(BroadcastHub.sink(256))(Keep.both)
      .withAttributes(ActorAttributes.logLevels(Logging.DebugLevel, Logging.DebugLevel, Logging.DebugLevel))
      .run()
  }

  /**
    * The "final form" of the event flow.
    *
    * It consumes byte strings which are the events, and feeds them to the hub inbound.
    *
    * The server does not need to write any data back to the event source (use
    * `Source.maybe` to represent something that does not write data, yet at the same
    * time does NOT complete the stream, otherwise the connection could be closed).
    *
    * Note that you still want the connection to be closed when the event source
    * is completed. Compare the documentation of `Flow.fromSinkAndSource` and
    * `Flow.fromSinkAndSourceCoupled` to find how to achieve that.
    */
  val eventsFlow: Flow[ByteString, Nothing, NotUsed] = {
    val source: Source[Nothing, Promise[Option[Nothing]]] = Source.maybe[Nothing]
    Flow.fromSinkAndSourceCoupled(inboundSink, source)

  }

  /**
    * @return The source of events for the given user
    * @param userId Id of the user
    *
    * Reminder on delivery semantics of messages:
    *
    * Follow:          Only the To User Id should be notified
    * Unfollow:        No clients should be notified
    * Broadcast:       All connected user clients should be notified
    * Private Message: Only the To User Id should be notified
    * Status Update:   All current followers of the From User ID should be notified
    */
  def outgoingFlow(userId: Int): Source[ByteString, NotUsed] = {
    broadcastOut.statefulMapConcat { () =>
      var followersMap: Map[Int, Set[Int]] = Map.empty[Int, Set[Int]]
      eventValue => eventValue._1 match {
        case f:Event.Follow => {
          List(ByteString.empty)
        }
        case u:Event.Unfollow => {
          List(ByteString.empty)
        }
        case s:Event.StatusUpdate => {
          List(ByteString.empty)
        }
        case _ => {
          List(ByteString.empty)
        }
      }
    }
  }

  /**
   * The "final form" of the client flow.
   *
   * Clients will connect to this server and send their id as an Identity message (e.g. "21323\n").
   *
   * The server should establish a link from the event source towards the clients, in such way that they
   * receive only the events that they are interested about.
   *
   * The incoming side of this flow needs to extract the client id to then properly construct the outgoing Source,
   * as it will need this identifier to notify the server which data it is interested about.
   *
   * Hints:
   *   - since the clientId will be emitted as a materialized value of `identityParserSink`,
   *     you may need to use mapMaterializedValue to side effect it into a shared Promise/Future that the Source
   *     side can utilise to construct such Source "from that client id future".
   *   - Remember to use `via()` to connect a `Flow`, and `to()` to connect a `Sink`.
   */
  def clientFlow(): Flow[ByteString, ByteString, NotUsed] = {
    val clientIdPromise = Promise[Identity]()

    // clientIdPromise.future.map(id => actorSystem.log.info("Connected follower: {}", id.userId))
    // A sink that parses the client identity and completes `clientIdPromise` with it

//    val incoming: Sink[ByteString, NotUsed] = MergeHub.source[ByteString].to(identityParserSink).run()
    val incoming: Sink[ByteString, NotUsed] = identityParserSink.mapMaterializedValue(q => NotUsed)

    val outgoing = Source.fromFutureSource(clientIdPromise.future.map { identity =>
      outgoingFlow(identity.userId)
    })

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}
