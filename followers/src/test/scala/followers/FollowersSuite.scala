package followers

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.Framing.FramingException
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorAttributes, ActorMaterializer, Materializer}
import akka.testkit.TestKit
import akka.util.ByteString
import followers.model.{Event, Identity}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Random

class FollowersSuite extends TestKit(ActorSystem("FollowersSuite"))
  with FunSuiteLike
  with BeforeAndAfterAll
  with ScalaFutures {

  import system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(3000, Millis)), interval = scaled(Span(1000, Millis)))

  override protected def afterAll(): Unit = {
    shutdown(system)
    super.afterAll()
  }

  test("reframedFlow: chunks containing exactly one message should pass through") {
    val incoming = List(ByteString("foo\n"), ByteString("bar\n"))
    val got = Source(incoming).via(Server.reframedFlow).runWith(Sink.seq).futureValue
    got === Seq("foo", "bar")
  }

  test("reframedFlow: chunks containing fragments of messages should be re-assembled") {
    val incoming = List(ByteString("f"), ByteString("oo\nb"), ByteString("ar\n"))
    val got = Source(incoming).via(Server.reframedFlow).runWith(Sink.seq).futureValue
    got === Seq("foo", "bar")
  }

  test("reframedFlow: reject an input stream that is completed in the middle of a frame") {
    val reframed = Source.single(ByteString("foo\nbar")).via(Server.reframedFlow).runWith(Sink.ignore)
    Await.ready(reframed, 1.second)
    assert(reframed.value.get.failed.get.isInstanceOf[FramingException])
  }

  test("eventParserFlow: successfully parse events") {
    val incoming = List(
      ByteString("666|F|6"), ByteString("0|50\n"),
      ByteString("1|U"), ByteString("|12|9\n"),
      ByteString("54232|B\n43|P|32|"), ByteString("56\n"),
      ByteString("634|S|32\n")
    )
    val got = Source(incoming).via(Server.eventParserFlow).runWith(Sink.seq).futureValue
    assert(got === Seq(
      Event.Follow(666, 60, 50),
      Event.Unfollow(1, 12, 9),
      Event.Broadcast(54232),
      Event.PrivateMsg(43, 32, 56),
      Event.StatusUpdate(634, 32)
    ))
  }

  test("reintroduceOrdering: pass through a sorted stream directly") {
//    val incoming = List.tabulate(1000)(n => Event.Follow(n + 1, 1, 1))
    val incoming = List (Event.Broadcast(5), Event.Broadcast(11), Event.Broadcast(15), Event.Broadcast(17), Event.Broadcast(19), Event.Broadcast(23), Event.Broadcast(25), Event.Broadcast(28), Event.Broadcast(31), Event.Broadcast(34), Event.Broadcast(35), Event.Broadcast(36), Event.Broadcast(37), Event.Broadcast(38), Event.Broadcast(40), Event.Broadcast(41), Event.Broadcast(43), Event.Broadcast(44), Event.Broadcast(50), Event.Broadcast(51), Event.Broadcast(53), Event.Broadcast(55), Event.Broadcast(59), Event.Broadcast(61), Event.Broadcast(62), Event.Broadcast(65), Event.Broadcast(68), Event.Broadcast(74), Event.Broadcast(79), Event.Broadcast(80), Event.Broadcast(81), Event.Broadcast(84), Event.Broadcast(86), Event.Broadcast(93), Event.Broadcast(95), Event.Broadcast(103), Event.Broadcast(105), Event.Broadcast(106), Event.Broadcast(108), Event.Broadcast(109), Event.Broadcast(112), Event.Broadcast(113), Event.Broadcast(116), Event.Broadcast(122), Event.Broadcast(124), Event.Broadcast(125), Event.Broadcast(128), Event.Broadcast(133), Event.Broadcast(136), Event.Broadcast(138), Event.Broadcast(139), Event.Broadcast(144), Event.Broadcast(147), Event.Broadcast(148), Event.Broadcast(150), Event.Broadcast(151), Event.Broadcast(152), Event.Broadcast(153), Event.Broadcast(155), Event.Broadcast(158), Event.Broadcast(159), Event.Broadcast(163), Event.Broadcast(164), Event.Broadcast(168), Event.Broadcast(169), Event.Broadcast(174), Event.Broadcast(177), Event.Broadcast(179), Event.Broadcast(185), Event.Broadcast(187), Event.Broadcast(189), Event.Broadcast(195), Event.Broadcast(200), Event.Broadcast(201), Event.Broadcast(205), Event.Broadcast(206), Event.Broadcast(209), Event.Broadcast(210), Event.Broadcast(217), Event.Broadcast(221), Event.Broadcast(224), Event.Broadcast(229), Event.Broadcast(231), Event.Broadcast(234), Event.Broadcast(235), Event.Broadcast(237), Event.Broadcast(238), Event.Broadcast(239), Event.Broadcast(240), Event.Broadcast(245), Event.Broadcast(248), Event.Broadcast(252), Event.Broadcast(253), Event.Broadcast(256), Event.Broadcast(260), Event.Broadcast(261), Event.Broadcast(265), Event.Broadcast(268), Event.Broadcast(269), Event.Broadcast(272), Event.Broadcast(273), Event.Broadcast(276), Event.Broadcast(278), Event.Broadcast(279), Event.Broadcast(281), Event.Broadcast(283), Event.Broadcast(284), Event.Broadcast(286), Event.Broadcast(287), Event.Broadcast(288), Event.Broadcast(289), Event.Broadcast(291), Event.Broadcast(293), Event.Broadcast(297), Event.Broadcast(302), Event.Broadcast(306), Event.Broadcast(308), Event.Broadcast(309), Event.Broadcast(312), Event.Broadcast(313), Event.Broadcast(316), Event.Broadcast(318), Event.Broadcast(326), Event.Broadcast(327), Event.Broadcast(329), Event.Broadcast(332), Event.Broadcast(334), Event.Broadcast(335), Event.Broadcast(338), Event.Broadcast(341), Event.Broadcast(343), Event.Broadcast(349), Event.Broadcast(354), Event.Broadcast(357), Event.Broadcast(366), Event.Broadcast(368), Event.Broadcast(375), Event.Broadcast(376), Event.Broadcast(377), Event.Broadcast(380), Event.Broadcast(383), Event.Broadcast(387), Event.Broadcast(388), Event.Broadcast(390), Event.Broadcast(391), Event.Broadcast(395), Event.Broadcast(396), Event.Broadcast(400), Event.Broadcast(401), Event.Broadcast(402), Event.Broadcast(404), Event.Broadcast(407), Event.Broadcast(408), Event.Broadcast(409), Event.Broadcast(412), Event.Broadcast(415), Event.Broadcast(418), Event.Broadcast(421), Event.Broadcast(422), Event.Broadcast(427), Event.Broadcast(431), Event.Broadcast(444), Event.Broadcast(445), Event.Broadcast(446), Event.Broadcast(454), Event.Broadcast(456), Event.Broadcast(460), Event.Broadcast(465), Event.Broadcast(466), Event.Broadcast(468), Event.Broadcast(475), Event.Broadcast(476), Event.Broadcast(477), Event.Broadcast(478), Event.Broadcast(479), Event.Broadcast(481), Event.Broadcast(483), Event.Broadcast(486), Event.Broadcast(488), Event.Broadcast(489), Event.Broadcast(494), Event.Broadcast(506), Event.Broadcast(508), Event.Broadcast(510), Event.Broadcast(513), Event.Broadcast(519), Event.Broadcast(520), Event.Broadcast(521), Event.Broadcast(526), Event.Broadcast(529), Event.Broadcast(530), Event.Broadcast(533), Event.Broadcast(534), Event.Broadcast(536), Event.Broadcast(541), Event.Broadcast(546), Event.Broadcast(547), Event.Broadcast(550), Event.Broadcast(551), Event.Broadcast(552), Event.Broadcast(554), Event.Broadcast(555), Event.Broadcast(566), Event.Broadcast(569), Event.Broadcast(571), Event.Broadcast(574), Event.Broadcast(576), Event.Broadcast(577), Event.Broadcast(578), Event.Broadcast(588), Event.Broadcast(589), Event.Broadcast(596), Event.Broadcast(599), Event.Broadcast(601), Event.Broadcast(606), Event.Broadcast(607), Event.Broadcast(608), Event.Broadcast(610), Event.Broadcast(611), Event.Broadcast(615), Event.Broadcast(616), Event.Broadcast(617), Event.Broadcast(629), Event.Broadcast(634), Event.Broadcast(635), Event.Broadcast(636), Event.Broadcast(637), Event.Broadcast(644), Event.Broadcast(655), Event.Broadcast(656), Event.Broadcast(657), Event.Broadcast(659), Event.Broadcast(663), Event.Broadcast(666), Event.Broadcast(670), Event.Broadcast(671), Event.Broadcast(676), Event.Broadcast(677), Event.Broadcast(679), Event.Broadcast(681), Event.Broadcast(683), Event.Broadcast(690), Event.Broadcast(692), Event.Broadcast(695), Event.Broadcast(697), Event.Broadcast(698), Event.Broadcast(703), Event.Broadcast(704), Event.Broadcast(706), Event.Broadcast(709), Event.Broadcast(711), Event.Broadcast(713), Event.Broadcast(715), Event.Broadcast(716), Event.Broadcast(718), Event.Broadcast(720), Event.Broadcast(737), Event.Broadcast(738), Event.Broadcast(741), Event.Broadcast(742), Event.Broadcast(744), Event.Broadcast(751), Event.Broadcast(752), Event.Broadcast(754), Event.Broadcast(757), Event.Broadcast(760), Event.Broadcast(763), Event.Broadcast(764), Event.Broadcast(765), Event.Broadcast(766), Event.Broadcast(767), Event.Broadcast(769), Event.Broadcast(772), Event.Broadcast(774), Event.Broadcast(775), Event.Broadcast(776), Event.Broadcast(777), Event.Broadcast(778), Event.Broadcast(779), Event.Broadcast(780), Event.Broadcast(781), Event.Broadcast(782), Event.Broadcast(784), Event.Broadcast(791), Event.Broadcast(794), Event.Broadcast(803), Event.Broadcast(804), Event.Broadcast(805), Event.Broadcast(806), Event.Broadcast(811), Event.Broadcast(812), Event.Broadcast(817), Event.Broadcast(823), Event.Broadcast(824), Event.Broadcast(825), Event.Broadcast(827), Event.Broadcast(828), Event.Broadcast(831), Event.Broadcast(834), Event.Broadcast(838), Event.Broadcast(842), Event.Broadcast(843), Event.Broadcast(845), Event.Broadcast(848), Event.Broadcast(849), Event.Broadcast(852), Event.Broadcast(853), Event.Broadcast(863), Event.Broadcast(867), Event.Broadcast(872), Event.Broadcast(874), Event.Broadcast(876), Event.Broadcast(882), Event.Broadcast(883), Event.Broadcast(884), Event.Broadcast(885), Event.Broadcast(887), Event.Broadcast(890), Event.Broadcast(893), Event.Broadcast(901), Event.Broadcast(907), Event.Broadcast(909), Event.Broadcast(912), Event.Broadcast(915), Event.Broadcast(917), Event.Broadcast(918), Event.Broadcast(925), Event.Broadcast(930), Event.Broadcast(933), Event.Broadcast(938), Event.Broadcast(941), Event.Broadcast(944), Event.Broadcast(949), Event.Broadcast(953), Event.Broadcast(955), Event.Broadcast(961), Event.Broadcast(965), Event.Broadcast(969), Event.Broadcast(972), Event.Broadcast(973), Event.Broadcast(975), Event.Broadcast(976), Event.Broadcast(977), Event.Broadcast(979), Event.Broadcast(983), Event.Broadcast(989), Event.Broadcast(994), Event.Broadcast(997), Event.Broadcast(1), Event.Broadcast(20), Event.Broadcast(57), Event.Broadcast(63), Event.Broadcast(71), Event.Broadcast(75), Event.Broadcast(82), Event.Broadcast(88), Event.Broadcast(118), Event.Broadcast(120), Event.Broadcast(121), Event.Broadcast(141), Event.Broadcast(149), Event.Broadcast(154), Event.Broadcast(156), Event.Broadcast(188), Event.Broadcast(194), Event.Broadcast(199), Event.Broadcast(213), Event.Broadcast(216), Event.Broadcast(244), Event.Broadcast(259), Event.Broadcast(285), Event.Broadcast(299), Event.Broadcast(300), Event.Broadcast(307), Event.Broadcast(310), Event.Broadcast(311), Event.Broadcast(372), Event.Broadcast(374), Event.Broadcast(378), Event.Broadcast(392), Event.Broadcast(434), Event.Broadcast(435), Event.Broadcast(437), Event.Broadcast(449), Event.Broadcast(461), Event.Broadcast(471), Event.Broadcast(485), Event.Broadcast(498), Event.Broadcast(500), Event.Broadcast(516), Event.Broadcast(522), Event.Broadcast(525), Event.Broadcast(548), Event.Broadcast(549), Event.Broadcast(553), Event.Broadcast(579), Event.Broadcast(621), Event.Broadcast(628), Event.Broadcast(633), Event.Broadcast(640), Event.Broadcast(641), Event.Broadcast(649), Event.Broadcast(672), Event.Broadcast(680), Event.Broadcast(685), Event.Broadcast(699), Event.Broadcast(707), Event.Broadcast(708), Event.Broadcast(728), Event.Broadcast(730), Event.Broadcast(732), Event.Broadcast(813), Event.Broadcast(815), Event.Broadcast(816), Event.Broadcast(819), Event.Broadcast(820), Event.Broadcast(841), Event.Broadcast(844), Event.Broadcast(857), Event.Broadcast(861), Event.Broadcast(869), Event.Broadcast(871), Event.Broadcast(880), Event.Broadcast(889), Event.Broadcast(908), Event.Broadcast(926), Event.Broadcast(932), Event.Broadcast(939), Event.Broadcast(940), Event.Broadcast(947), Event.Broadcast(950), Event.Broadcast(971), Event.Broadcast(995), Event.Broadcast(16), Event.Broadcast(21), Event.Broadcast(22), Event.Broadcast(27), Event.Broadcast(32), Event.Broadcast(33), Event.Broadcast(46), Event.Broadcast(48), Event.Broadcast(56), Event.Broadcast(72), Event.Broadcast(73), Event.Broadcast(77), Event.Broadcast(85), Event.Broadcast(96), Event.Broadcast(98), Event.Broadcast(99), Event.Broadcast(115), Event.Broadcast(130), Event.Broadcast(137), Event.Broadcast(157), Event.Broadcast(173), Event.Broadcast(184), Event.Broadcast(198), Event.Broadcast(204), Event.Broadcast(207), Event.Broadcast(220), Event.Broadcast(225), Event.Broadcast(227), Event.Broadcast(228), Event.Broadcast(230), Event.Broadcast(232), Event.Broadcast(242), Event.Broadcast(246), Event.Broadcast(249), Event.Broadcast(255), Event.Broadcast(257), Event.Broadcast(274), Event.Broadcast(282), Event.Broadcast(296), Event.Broadcast(305), Event.Broadcast(319), Event.Broadcast(324), Event.Broadcast(325), Event.Broadcast(345), Event.Broadcast(356), Event.Broadcast(358), Event.Broadcast(362), Event.Broadcast(367), Event.Broadcast(381), Event.Broadcast(399), Event.Broadcast(406), Event.Broadcast(430), Event.Broadcast(438), Event.Broadcast(442), Event.Broadcast(453), Event.Broadcast(463), Event.Broadcast(464), Event.Broadcast(467), Event.Broadcast(487), Event.Broadcast(491), Event.Broadcast(507), Event.Broadcast(509), Event.Broadcast(528), Event.Broadcast(532), Event.Broadcast(538), Event.Broadcast(539), Event.Broadcast(568), Event.Broadcast(585), Event.Broadcast(593), Event.Broadcast(594), Event.Broadcast(609), Event.Broadcast(618), Event.Broadcast(623), Event.Broadcast(625), Event.Broadcast(627), Event.Broadcast(632), Event.Broadcast(643), Event.Broadcast(647), Event.Broadcast(651), Event.Broadcast(661), Event.Broadcast(667), Event.Broadcast(668), Event.Broadcast(693), Event.Broadcast(717), Event.Broadcast(721), Event.Broadcast(724), Event.Broadcast(727), Event.Broadcast(729), Event.Broadcast(736), Event.Broadcast(753), Event.Broadcast(758), Event.Broadcast(759), Event.Broadcast(761), Event.Broadcast(790), Event.Broadcast(797), Event.Broadcast(800), Event.Broadcast(801), Event.Broadcast(822), Event.Broadcast(829), Event.Broadcast(830), Event.Broadcast(835), Event.Broadcast(851), Event.Broadcast(859), Event.Broadcast(879), Event.Broadcast(897), Event.Broadcast(903), Event.Broadcast(906), Event.Broadcast(922), Event.Broadcast(923), Event.Broadcast(966), Event.Broadcast(967), Event.Broadcast(970), Event.Broadcast(985), Event.Broadcast(996), Event.Broadcast(2), Event.Broadcast(10), Event.Broadcast(12), Event.Broadcast(18), Event.Broadcast(24), Event.Broadcast(30), Event.Broadcast(47), Event.Broadcast(49), Event.Broadcast(52), Event.Broadcast(69), Event.Broadcast(78), Event.Broadcast(83), Event.Broadcast(87), Event.Broadcast(100), Event.Broadcast(101), Event.Broadcast(110), Event.Broadcast(123), Event.Broadcast(135), Event.Broadcast(161), Event.Broadcast(166), Event.Broadcast(167), Event.Broadcast(170), Event.Broadcast(171), Event.Broadcast(176), Event.Broadcast(180), Event.Broadcast(181), Event.Broadcast(196), Event.Broadcast(203), Event.Broadcast(208), Event.Broadcast(211), Event.Broadcast(212), Event.Broadcast(218), Event.Broadcast(241), Event.Broadcast(243), Event.Broadcast(262), Event.Broadcast(270), Event.Broadcast(277), Event.Broadcast(294), Event.Broadcast(295), Event.Broadcast(301), Event.Broadcast(303), Event.Broadcast(315), Event.Broadcast(317), Event.Broadcast(322), Event.Broadcast(323), Event.Broadcast(330), Event.Broadcast(339), Event.Broadcast(340), Event.Broadcast(344), Event.Broadcast(346), Event.Broadcast(347), Event.Broadcast(348), Event.Broadcast(355), Event.Broadcast(360), Event.Broadcast(361), Event.Broadcast(365), Event.Broadcast(373), Event.Broadcast(379), Event.Broadcast(385), Event.Broadcast(393), Event.Broadcast(394), Event.Broadcast(411), Event.Broadcast(416), Event.Broadcast(419), Event.Broadcast(424), Event.Broadcast(432), Event.Broadcast(439), Event.Broadcast(440), Event.Broadcast(443), Event.Broadcast(450), Event.Broadcast(452), Event.Broadcast(458), Event.Broadcast(459), Event.Broadcast(470), Event.Broadcast(497), Event.Broadcast(499), Event.Broadcast(501), Event.Broadcast(505), Event.Broadcast(511), Event.Broadcast(518), Event.Broadcast(523), Event.Broadcast(524), Event.Broadcast(531), Event.Broadcast(537), Event.Broadcast(540), Event.Broadcast(545), Event.Broadcast(557), Event.Broadcast(561), Event.Broadcast(564), Event.Broadcast(565), Event.Broadcast(573), Event.Broadcast(584), Event.Broadcast(587), Event.Broadcast(590), Event.Broadcast(595), Event.Broadcast(597), Event.Broadcast(600), Event.Broadcast(602), Event.Broadcast(603), Event.Broadcast(604), Event.Broadcast(612), Event.Broadcast(613), Event.Broadcast(614), Event.Broadcast(622), Event.Broadcast(631), Event.Broadcast(645), Event.Broadcast(652), Event.Broadcast(664), Event.Broadcast(674), Event.Broadcast(675), Event.Broadcast(678), Event.Broadcast(682), Event.Broadcast(684), Event.Broadcast(688), Event.Broadcast(700), Event.Broadcast(701), Event.Broadcast(705), Event.Broadcast(714), Event.Broadcast(725), Event.Broadcast(731), Event.Broadcast(733), Event.Broadcast(740), Event.Broadcast(745), Event.Broadcast(747), Event.Broadcast(749), Event.Broadcast(750), Event.Broadcast(773), Event.Broadcast(787), Event.Broadcast(788), Event.Broadcast(793), Event.Broadcast(795), Event.Broadcast(796), Event.Broadcast(798), Event.Broadcast(802), Event.Broadcast(821), Event.Broadcast(833), Event.Broadcast(854), Event.Broadcast(856), Event.Broadcast(858), Event.Broadcast(862), Event.Broadcast(864), Event.Broadcast(865), Event.Broadcast(866), Event.Broadcast(873), Event.Broadcast(888), Event.Broadcast(891), Event.Broadcast(892), Event.Broadcast(895), Event.Broadcast(896), Event.Broadcast(898), Event.Broadcast(899), Event.Broadcast(911), Event.Broadcast(919), Event.Broadcast(920), Event.Broadcast(924), Event.Broadcast(927), Event.Broadcast(929), Event.Broadcast(937), Event.Broadcast(942), Event.Broadcast(945), Event.Broadcast(946), Event.Broadcast(952), Event.Broadcast(957), Event.Broadcast(958), Event.Broadcast(959), Event.Broadcast(963), Event.Broadcast(982), Event.Broadcast(986), Event.Broadcast(990), Event.Broadcast(992), Event.Broadcast(993), Event.Broadcast(999), Event.Broadcast(4), Event.Broadcast(6), Event.Broadcast(7), Event.Broadcast(8), Event.Broadcast(9), Event.Broadcast(13), Event.Broadcast(14), Event.Broadcast(26), Event.Broadcast(29), Event.Broadcast(39), Event.Broadcast(42), Event.Broadcast(45), Event.Broadcast(60), Event.Broadcast(64), Event.Broadcast(66), Event.Broadcast(70), Event.Broadcast(76), Event.Broadcast(89), Event.Broadcast(90), Event.Broadcast(91), Event.Broadcast(94), Event.Broadcast(97), Event.Broadcast(104), Event.Broadcast(107), Event.Broadcast(114), Event.Broadcast(119), Event.Broadcast(126), Event.Broadcast(127), Event.Broadcast(131), Event.Broadcast(132), Event.Broadcast(140), Event.Broadcast(142), Event.Broadcast(146), Event.Broadcast(165), Event.Broadcast(172), Event.Broadcast(175), Event.Broadcast(178), Event.Broadcast(182), Event.Broadcast(183), Event.Broadcast(190), Event.Broadcast(191), Event.Broadcast(192), Event.Broadcast(197), Event.Broadcast(214), Event.Broadcast(215), Event.Broadcast(222), Event.Broadcast(223), Event.Broadcast(226), Event.Broadcast(233), Event.Broadcast(236), Event.Broadcast(247), Event.Broadcast(250), Event.Broadcast(251), Event.Broadcast(258), Event.Broadcast(263), Event.Broadcast(267), Event.Broadcast(271), Event.Broadcast(275), Event.Broadcast(280), Event.Broadcast(290), Event.Broadcast(292), Event.Broadcast(298), Event.Broadcast(314), Event.Broadcast(320), Event.Broadcast(321), Event.Broadcast(328), Event.Broadcast(331), Event.Broadcast(333), Event.Broadcast(336), Event.Broadcast(337), Event.Broadcast(342), Event.Broadcast(350), Event.Broadcast(351), Event.Broadcast(352), Event.Broadcast(353), Event.Broadcast(359), Event.Broadcast(363), Event.Broadcast(369), Event.Broadcast(370), Event.Broadcast(371), Event.Broadcast(382), Event.Broadcast(389), Event.Broadcast(397), Event.Broadcast(405), Event.Broadcast(410), Event.Broadcast(413), Event.Broadcast(414), Event.Broadcast(420), Event.Broadcast(423), Event.Broadcast(425), Event.Broadcast(426), Event.Broadcast(428), Event.Broadcast(429), Event.Broadcast(433), Event.Broadcast(447), Event.Broadcast(448), Event.Broadcast(451), Event.Broadcast(457), Event.Broadcast(462), Event.Broadcast(469), Event.Broadcast(472), Event.Broadcast(473), Event.Broadcast(474), Event.Broadcast(484), Event.Broadcast(490), Event.Broadcast(492), Event.Broadcast(493), Event.Broadcast(495), Event.Broadcast(496), Event.Broadcast(503), Event.Broadcast(504), Event.Broadcast(512), Event.Broadcast(515), Event.Broadcast(517), Event.Broadcast(527), Event.Broadcast(535), Event.Broadcast(542), Event.Broadcast(543), Event.Broadcast(544), Event.Broadcast(556), Event.Broadcast(558), Event.Broadcast(559), Event.Broadcast(562), Event.Broadcast(563), Event.Broadcast(567), Event.Broadcast(572), Event.Broadcast(580), Event.Broadcast(581), Event.Broadcast(582), Event.Broadcast(583), Event.Broadcast(586), Event.Broadcast(591), Event.Broadcast(592), Event.Broadcast(598), Event.Broadcast(605), Event.Broadcast(619), Event.Broadcast(620), Event.Broadcast(624), Event.Broadcast(626), Event.Broadcast(630), Event.Broadcast(638), Event.Broadcast(639), Event.Broadcast(642), Event.Broadcast(646), Event.Broadcast(650), Event.Broadcast(653), Event.Broadcast(658), Event.Broadcast(660), Event.Broadcast(662), Event.Broadcast(665), Event.Broadcast(686), Event.Broadcast(689), Event.Broadcast(691), Event.Broadcast(694), Event.Broadcast(696), Event.Broadcast(710), Event.Broadcast(719), Event.Broadcast(722), Event.Broadcast(723), Event.Broadcast(726), Event.Broadcast(734), Event.Broadcast(735), Event.Broadcast(739), Event.Broadcast(743), Event.Broadcast(746), Event.Broadcast(748), Event.Broadcast(755), Event.Broadcast(756), Event.Broadcast(768), Event.Broadcast(770), Event.Broadcast(771), Event.Broadcast(783), Event.Broadcast(785), Event.Broadcast(786), Event.Broadcast(792), Event.Broadcast(799), Event.Broadcast(807), Event.Broadcast(809), Event.Broadcast(814), Event.Broadcast(818), Event.Broadcast(832), Event.Broadcast(836), Event.Broadcast(839), Event.Broadcast(846), Event.Broadcast(847), Event.Broadcast(850), Event.Broadcast(855), Event.Broadcast(860), Event.Broadcast(868), Event.Broadcast(870), Event.Broadcast(875), Event.Broadcast(881), Event.Broadcast(886), Event.Broadcast(894), Event.Broadcast(900), Event.Broadcast(902), Event.Broadcast(905), Event.Broadcast(913), Event.Broadcast(914), Event.Broadcast(916), Event.Broadcast(928), Event.Broadcast(931), Event.Broadcast(934), Event.Broadcast(935), Event.Broadcast(936), Event.Broadcast(943), Event.Broadcast(951), Event.Broadcast(954), Event.Broadcast(956), Event.Broadcast(960), Event.Broadcast(962), Event.Broadcast(968), Event.Broadcast(974), Event.Broadcast(978), Event.Broadcast(980), Event.Broadcast(981), Event.Broadcast(3), Event.Broadcast(984), Event.Broadcast(987), Event.Broadcast(988), Event.Broadcast(991), Event.Broadcast(1000))

    val sorted = Source(incoming) via Server.reintroduceOrdering
    val got = sorted.runWith(Sink.seq).futureValue
    assert(got === incoming.sortBy(_.sequenceNr))
  }

  test("reintroduceOrdering: reintroduce ordering, 2 off") {
    val incoming = List(
      Event.Follow(2, 1, 2),
      Event.Follow(1, 1, 3),
      Event.Follow(4, 1, 4),
      Event.Follow(3, 1, 4)
    )

    val sorted = Source(incoming) via Server.reintroduceOrdering
    val got = sorted.runWith(Sink.seq).futureValue
    assert(got === incoming.sortBy(_.sequenceNr))
  }

  test("followersFlow: add a follower") {
    val got =
      Source(List(Event.Follow(1, 1, 2), Event.Follow(2, 1, 3)))
        .via(Server.followersFlow)
        .runWith(Sink.seq)
        .futureValue
    assert(got === Seq((Event.Follow(1, 1, 2), Map(1 -> Set(2))), (Event.Follow(2, 1, 3), Map(1 -> Set(2, 3)))))
  }

  test("followersFlow: remove a follower") {
    val got =
      Source(List(Event.Follow(1, 1, 2), Event.Unfollow(2, 1, 2)))
        .via(Server.followersFlow)
        .runWith(Sink.seq)
        .futureValue
    assert(got === Seq(
      (Event.Follow(1, 1, 2), Map(1 -> Set(2))),
      (Event.Unfollow(2, 1, 2), Map(1 -> Set.empty))
    ))
  }

  test("identityParserSink: extract identity") {
    assert(Source.single(ByteString("42\nignored"))
      .runWith(Server.identityParserSink).futureValue === Identity(42))
  }

  test("identityParserSink: re-frame incoming bytes") {
    val sink: Sink[ByteString, Future[Identity]] = Server.identityParserSink
    assert(Source(List(
      ByteString("1"), ByteString("2\n"),
      ByteString("ignored"), ByteString("\n")
    )).runWith(sink).futureValue === Identity(12))
  }

  test("isNotified: always notify users of broadcast messages") {
    for(userId <- 1 to 1000) {
      assert(Server.isNotified(userId)((Event.Broadcast(1), Map.empty)))
    }
  }

  test("isNotified: notify the followers of an user that updates his status") {
    assert(Server.isNotified(42)((Event.StatusUpdate(1, 12), Map(42 -> Set(12)))))
  }

  test("eventsFlow: downstream should receive completion when the event source is completed") {
    val server = new Server()
    // Feed the server with no events
    val eventsProbe = connectEvents(server)()
    // Check that no events are emitted by the hub
    val outProbe = server.broadcastOut.runWith(TestSink.probe)
    outProbe.ensureSubscription().request(Int.MaxValue)
    outProbe.expectNoMessage(10.millis)
    // The event source should complete without receiving any message
    eventsProbe.ensureSubscription().request(1).expectComplete()
  }

  test("incomingDataFlow: reframe, reorder and compute followers") {
    val server = new Server()
    val eventsProbe = connectEvents(server)(
      Event.StatusUpdate(2, 2),
      Event.Follow(1, 1, 2)
    )
    val outProbe = server.broadcastOut.runWith(TestSink.probe)
    outProbe.ensureSubscription().request(Int.MaxValue)
    outProbe.expectNext((Event.Follow(1, 1, 2), Map(1 -> Set(2))))
    outProbe.expectNext((Event.StatusUpdate(2, 2), Map(1 -> Set(2))))
    outProbe.expectNoMessage(50.millis)
    eventsProbe.ensureSubscription().request(1).expectComplete()
  }

  test("outgoingFlow: filter out events that should not be delivered to the given user") {
    val server = new Server()
    val client1 = connectClient(1, server)
    val client2 = connectClient(2, server)
    val client3 = connectClient(3, server)
    // Note that we have connected the clients before emitting
    // the events so that the clients won’t miss the events
    val eventsProbe = connectEvents(server)(
      Event.StatusUpdate(2, 2),
      Event.Follow(1, 1, 2)
    )

    client1.expectNext(Event.StatusUpdate(2, 2))
    client1.expectNoMessage(50.millis)
    client2.expectNext(Event.Follow(1, 1, 2))
    client2.expectNoMessage(50.millis)
    client3.expectNoMessage(50.millis)
    eventsProbe.ensureSubscription().request(1).expectComplete()
  }

  test("clientFlow: handle one client following another") {
    val server = new Server()
    val follower1 = connectClient(1, server)
    val follower2 = connectClient(2, server)
    val eventsProbe = connectEvents(server)(
      Event.Follow(1, 1, 2),
      Event.StatusUpdate(2, 2),
      Event.StatusUpdate(3, 1)
    )
    // ---- setup done ----

    follower2.expectNext(Event.Follow(1, 1, 2))
    follower2.expectNoMessage(50.millis)
    follower1.expectNext(Event.StatusUpdate(2, 2))
    follower1.expectNoMessage(50.millis)
    eventsProbe.ensureSubscription().request(1).expectComplete()
  }

  test("clientFlow: ensure that event before issuing a Follow is not sent to that follower") {

    val server = new Server()
    val follower1 = connectClient(1, server)
    val follower2 = connectClient(2, server)
    val eventsProbe = connectEvents(server)(
      Event.StatusUpdate(1, 1),
      Event.StatusUpdate(2, 2),
      Event.Follow(3, 1, 2)
    )
    // ---- setup done ----

    follower2.expectNext(Event.Follow(3, 1, 2))
    follower2.expectNoMessage(50.millis)
    eventsProbe.ensureSubscription().request(1).expectComplete()
  }

  test("clientFlow: ensure that users get notified from private messages") {
    val server = new Server()
    val follower1 = connectClient(1, server)
    val follower2 = connectClient(2, server)
    val follower3 = connectClient(3, server)
    val eventsProbe = connectEvents(server)(
      Event.Follow(1, 1, 3),
      Event.PrivateMsg(2, 1, 2)
    )
    // ---- setup done ----

    follower2.expectNext(Event.PrivateMsg(2, 1, 2))
    follower2.expectNoMessage(50.millis)

    follower1.expectNoMessage(50.millis)

    follower3.expectNext(Event.Follow(1, 1, 3))
    follower3.expectNoMessage(50.millis) // We don’t receive private messages targeted to user 2
    eventsProbe.ensureSubscription().request(1).expectComplete()
  }

  /**
    * Connects a user to the server and gets its event feed
    * @return A probe for the event feed
    */
  def connectClient(id: Int, server: Server): TestSubscriber.Probe[Event] = {
    Source.single(ByteString(s"$id\n"))
      .withAttributes(ActorAttributes.logLevels(Logging.InfoLevel, Logging.InfoLevel, Logging.InfoLevel))
      .async
      .via(server.clientFlow())
      .via(Server.eventParserFlow)
      .runWith(TestSink.probe)
      .ensureSubscription()
      .request(Int.MaxValue)
  }

  /**
    * Connects a feed of events to the server
    * @return A probe for the server response
    */
  def connectEvents(server: Server)(events: Event*): TestSubscriber.Probe[Nothing] = {
    Source(events.toList)
      .via(Flow[Event]/*.logAllEvents("write-events")*/)
      .map(_.render).async
      .via(server.eventsFlow)
      .runWith(TestSink.probe)
  }

}

