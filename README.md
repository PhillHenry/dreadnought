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