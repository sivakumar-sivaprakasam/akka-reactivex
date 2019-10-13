println("Hello")

val f: PartialFunction[String, String] = {
  case "Ping" => "Pong"
}

f("Ping")
f.isDefinedAt("pong")

val f: PartialFunction[List[Int], String] = {
  case Nil => "One"
  case x :: y :: rest => "Two"
}

f.isDefinedAt(List(1, 2, 3))

val g: PartialFunction[List[Int], String] = {
  case Nil => "One"
  case x :: rest => rest match {
    case Nil => "Two"
  }
}

g.isDefinedAt(List(1, 2, 3))