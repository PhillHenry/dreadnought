package uk.co.odinconsultants.dreadnought.docker

opaque type ImageName     = String
opaque type ConnectionURL = String
opaque type ContainerId   = String
opaque type Command       = String
type DnsMapping[T]        = List[(T, T)]
type NetworkMapping[T]    = List[(T, T)]
type Environment          = List[String]

object ConnectionURL:
  def apply(x: String): ConnectionURL = x
object ImageName:
  def apply(x: String): ImageName = x
object ContainerId:
  def apply(x: String): ContainerId = x
object Command:
  def apply(x: String): Command = x

sealed abstract class ManagerRequest[A]

case class StartRequest(
    image: ImageName,
    command: Command,
    env: Environment,
    networkMappings: NetworkMapping[Int],
    dnsMappings: DnsMapping[String],
) extends ManagerRequest[ContainerId]

case class StopRequest(containerId: ContainerId)  extends ManagerRequest[Unit]
case class NamesRequest(containerId: ContainerId) extends ManagerRequest[List[String]]

object Domain {}
