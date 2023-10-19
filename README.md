# Dreadnought
Makes virtualization easy.

Synopsis
--
This project aims to make deploying containers to any manager (local Docker, cloud K8s etc) easier.

The code should be agnostic to whether it's deployed locally or remotely, in Docker or Kubernetes.

What's more, testing the flow of data through a full stack should be easy. Some examples are included.

Advantages
--
Rather than messing around with YAML to get `docker-compose` to run a collection of applications, 
achieve the same result in pure Scala code. You can even monitor the logs of your applications
and wait for certain events in them.

What's more, you can dynamically bring containers up and down in your code. 
For example, you can see what happens when you're producing and consumer Kafka events when one broker suddenly dies...

Caveats
--
Currently, Dreadnought only works on Unix-like system as it communicates with the Docker daemon via Unix sockets.

At the moment, Dreadnought will not download the Docker images if they're not already installed. 
A fix for this should be coming soon.

Building
--
To run examples (and build the documentation at the same time), run

```
sbt docs/mdoc
```
Markdown documents will then appear in the `target` folder