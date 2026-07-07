# Kubernetes Training Tutorial

> A hands-on, progressive guide from Docker containers to production-ready Kubernetes clusters. Each section builds on the previous one — follow them in order for the best learning experience.

## Learning Roadmap

```
Docker Basics ──► Pods ──► Labels ──► Namespaces ──► Volumes ──► Storage
                                                                    │
Monitoring ◄── RBAC ◄── Scheduling ◄── StatefulSets ◄── Services ◄─┘
     │                                                      ▲
     └── Helm                  Deployments ──► ReplicaSets ─┘
```

**Prerequisites:** Linux CLI basics, a machine with Docker installed, access to a Kubernetes cluster (Kind works great locally).

## Table of Contents

- [Docker Basics](#docker-basics)
- [Docker Networking](#docker-networking)
- [Setting Up a Local Cluster with Kind](#setting-up-a-local-cluster-with-kind)
- [Pod Basics](#pod-basics)
- [Pod Labels](#pod-labels)
- [Namespaces](#namespaces)
- [Volumes](#volumes)
- [Dynamic Storage Provisioning](#dynamic-storage-provisioning)
- [Pod Resources & LimitRange](#pod-resources--limitrange)
- [Pod Health Checks](#pod-health-checks)
- [ConfigMaps & Secrets](#configmaps--secrets)
- [Multi-Container Pods](#multi-container-pods)
- [Multi-Node Cluster with Kind](#multi-node-cluster-with-kind)
- [ReplicaSets](#replicasets)
- [Deployments](#deployments)
- [Services](#services)
- [Service Type: NodePort](#service-type-nodeport)
- [Service Type: LoadBalancer](#service-type-loadbalancer)
- [StatefulSets](#statefulsets)
- [Pod Scheduling](#pod-scheduling)
- [RBAC Policies](#rbac-policies)
- [Monitoring with Helm](#monitoring-with-helm)

---

## Docker Basics

> **Why this matters:** Kubernetes doesn't run your code directly — it runs containers. If you don't understand how containers are built and work, Kubernetes will feel like magic (the bad kind). Master Docker first.

### What is a Virtual Machine (VM)?

A VM is a complete emulation of a physical computer. It runs its own full operating system (guest OS) on top of a **hypervisor** that manages hardware resources. Each VM includes a complete OS kernel, system libraries, and the application.

```
Virtual Machine Architecture:
┌──────────────────────────────────────────────────┐
│              Physical Server                     │
│                                                  │
│  ┌──────────────┐  ┌──────────────┐              │
│  │     VM 1     │  │     VM 2     │              │
│  │  App A       │  │  App B       │              │
│  │  Bins/Libs   │  │  Bins/Libs   │              │
│  │  Guest OS    │  │  Guest OS    │  ← Full OS   │
│  │  (Ubuntu)    │  │  (CentOS)    │    per VM    │
│  └──────────────┘  └──────────────┘              │
│  ─────────── Hypervisor (VMware/KVM) ─────────── │
│  ─────────── Host OS ─────────────────────────── │
│  ─────────── Hardware ────────────────────────── │
└──────────────────────────────────────────────────┘
```

### What is a Hypervisor?

A hypervisor is software that creates and manages virtual machines by abstracting physical hardware. It allocates CPU, memory, disk, and network to each VM, allowing multiple isolated operating systems to run on a single physical machine.

**Two types:**

| Type | Name | Where it runs | Examples |
|------|------|---------------|----------|
| Type 1 | Bare-metal | Directly on hardware (no host OS) | VMware ESXi, KVM, Microsoft Hyper-V |
| Type 2 | Hosted | On top of a host OS as an application | VirtualBox, VMware Workstation |

```
Type 1 (Bare-metal):              Type 2 (Hosted):
┌──────┐ ┌──────┐                 ┌──────┐ ┌──────┐
│ VM 1 │ │ VM 2 │                 │ VM 1 │ │ VM 2 │
└──┬───┘ └──┬───┘                 └──┬───┘ └──┬───┘
   └────┬────┘                       └────┬────┘
   Hypervisor                         Hypervisor (app)
   Hardware                           Host OS
                                      Hardware
```

### Problems with VMs and Traditional Deployments

VMs solved the problem of running multiple isolated applications on a single physical server, but they come with significant drawbacks:

- **Heavy resource overhead** — Each VM runs a full guest OS (kernel, systemd, drivers), consuming 2–20 GB of disk and significant RAM just for the OS layer.
- **Slow startup** — Booting a VM takes 30–60 seconds or more because it must initialize an entire OS.
- **Poor density** — A typical laptop can run 5–10 VMs at most. A production server might handle 20–50 VMs before running out of resources.
- **Dependency conflicts still exist within a VM** — If you run multiple apps inside one VM, you're back to the same shared-library conflicts.
- **Slow provisioning** — Creating a new VM means installing the OS, dependencies, configuring networking, security patches — hours or days of work.
- **Environment drift** — Dev, staging, and production VMs slowly diverge. What works in dev breaks in production.
- **Snowflake servers** — Each VM becomes unique due to manual changes over time. Rebuilding one from scratch is painful or impossible.

```
Traditional Deployment (bare metal or VM):
┌──────────────────────────────────────────────────┐
│              Physical Server / VM                │
│                                                  │
│  App A (Python 3.8)    App B (Python 3.11)       │
│  Library X v1.2        Library X v2.0  ← CONFLICT│
│  Config files          Config files              │
│                                                  │
│  ──────── Shared OS (Ubuntu 20.04) ──────────    │
│  ──────── Shared Libraries ──────────────────    │
└──────────────────────────────────────────────────┘
Problem: Apps compete for shared resources and dependencies
```

### What is Docker?

Docker is a platform that packages your application and all its dependencies (libraries, runtime, system tools) into a standardized unit called a **container**. This container can run on any machine that has Docker installed — regardless of the underlying OS configuration. "It works on my machine" becomes "it works on every machine."

**Docker solves the VM and traditional deployment problems** by isolating each application in its own container with its own filesystem, dependencies, and network — while sharing the host OS kernel for efficiency.

```
Docker Deployment:
┌─────────────────────────────────────────────────┐
│              Host Machine (Linux)               │
│                                                 │
│  ┌─────────────────┐   ┌─────────────────┐      │
│  │ Container A     │   │ Container B     │      │
│  │ App A           │   │ App B           │      │
│  │ Python 3.8      │   │ Python 3.11     │      │
│  │ Library X v1.2  │   │ Library X v2.0  │      │
│  └─────────────────┘   └─────────────────┘      │
│         ▲                     ▲                 │
│         └───── Docker Engine ─┘                 │
│         ────── Host OS Kernel ──────────        │
└─────────────────────────────────────────────────┘
No conflicts: each container has its own isolated filesystem
```

### Merits of Docker over VMs

| Aspect | Docker Container | Virtual Machine |
|--------|-----------------|-----------------|
| **OS** | Shares host kernel | Runs its own full OS kernel |
| **Size** | Megabytes (50–500 MB) | Gigabytes (2–20 GB) |
| **Startup time** | Seconds | Minutes |
| **Performance** | Near-native (no hypervisor overhead) | ~5-20% overhead from virtualization |
| **Isolation** | Process-level (namespaces/cgroups) | Hardware-level (full OS boundary) |
| **Resource usage** | Lightweight — run 50+ on a laptop | Heavy — maybe 5-10 on a laptop |
| **Security** | Weaker isolation (shared kernel) | Stronger isolation (separate kernels) |
| **Portability** | Image runs anywhere Docker exists | VM image tied to hypervisor format |
| **Use case** | Microservices, stateless apps, CI/CD | Legacy apps, different OS needs, strong isolation |

```
Side-by-side comparison:

  VM Approach:                    Docker Approach:
  ┌─────────────┐                  ┌─────────────┐
  │ App (50MB)  │                  │ App (50MB)  │
  │ Libs (200MB)│                  │ Libs (200MB)│
  │ Guest OS    │ ← 2GB+           └─────────────┘
  │ (kernel,    │                       │
  │  systemd,   │                  Shares host kernel
  │  drivers)   │                  (no extra OS overhead)
  └─────────────┘
  Total: ~2.5GB                   Total: ~250MB
  Boot: 30-60 seconds             Boot: <1 second
```

**When to use VMs over Docker:**
- You need to run a different OS (e.g., Windows on a Linux host)
- You need strong security isolation between tenants
- You're running legacy applications that require a full OS environment

**When to use Docker over VMs:**
- Microservices architecture
- CI/CD pipelines (fast build/test/deploy cycles)
- Running many instances of the same application
- Development environments that match production

> **Key Takeaway:** Docker containers are NOT lightweight VMs. They are isolated processes sharing the host kernel. This is why they're fast and efficient — but also why they provide weaker isolation than VMs. In production, many organizations run containers inside VMs to get the benefits of both.

### How Docker Works Internally

Docker uses **Linux kernel features** to create isolated environments without needing a full OS for each container:

1. **Namespaces** — provide isolation. Each container gets its own view of:
   - PID namespace → container sees only its own processes (PID 1 is the app)
   - Network namespace → container gets its own IP address, ports, routing table
   - Mount namespace → container has its own filesystem
   - User namespace → container can have its own root user (mapped to non-root on host)

2. **cgroups (Control Groups)** — limit and track resource usage:
   - How much CPU a container can use
   - How much memory it can consume
   - Disk I/O bandwidth limits

3. **Union Filesystem (OverlayFS)** — layers images efficiently:
   - Base layer: Ubuntu OS files (read-only)
   - Next layer: Python installed (read-only)
   - Next layer: Your app code (read-only)
   - Top layer: Runtime changes (read-write)
   - Layers are shared between containers — 10 containers using the same base image don't duplicate it 10 times

```
How a container runs:
┌──────────────────────────────────────────────────────┐
│  docker run nginx                                    │
│       │                                              │
│       ▼                                              │
│  Docker Engine (daemon)                              │
│       │                                              │
│       ├── Creates namespaces (PID, NET, MNT, etc.)   │
│       ├── Sets up cgroups (CPU/memory limits)        │
│       ├── Mounts image layers via OverlayFS          │
│       ├── Sets up virtual network interface          │
│       └── Starts the process (nginx)                 │
│                                                      │
│  Result: nginx runs as an isolated process on the    │
│  host, thinking it's alone on its own machine        │
└──────────────────────────────────────────────────────┘
```

### Does Docker Use a Hypervisor?

**No. Docker does NOT use a hypervisor.** This is the fundamental difference between containers and VMs.

- **VMs:** App → Guest OS → Hypervisor → Hardware
- **Docker:** App → Docker Engine → Host Linux Kernel → Hardware

Docker talks directly to the host Linux kernel using namespaces and cgroups. There is no hardware emulation, no guest OS, no hypervisor layer. This is why containers start in seconds and use minimal resources.

**The exception — Docker on macOS/Windows:**

Containers require a Linux kernel. On non-Linux systems, Docker must run a lightweight Linux VM to provide that kernel:

| Platform | What happens under the hood |
|----------|----------------------------|
| Linux | Docker uses the host kernel directly — no VM, no hypervisor |
| macOS | Docker Desktop runs a Linux VM via Apple's Virtualization framework (or HyperKit) |
| Windows | Docker Desktop uses WSL2 (a lightweight Linux VM managed by Hyper-V) |

```
Docker on Linux:                  Docker on macOS/Windows:
┌───────────┐                     ┌───────────┐
│ Container │                     │ Container │
└─────┬─────┘                     └─────┬─────┘
  Docker Engine                     Docker Engine
  Host Linux Kernel ← direct        Linux VM kernel ← needs a VM
  Hardware                          Hypervisor (HyperKit/Hyper-V)
                                    Host OS (macOS/Windows)
                                    Hardware
```

> **Key point:** When people say "Docker doesn't use a hypervisor," they mean the container itself doesn't run inside a VM. On Linux, this is literally true. On macOS/Windows, Docker needs a VM only to provide a Linux kernel — the containers still run as isolated processes on that kernel, not as individual VMs.

---

**Key concept:** A container is an isolated process with its own filesystem, network, and process space — but it shares the host kernel. An image is the blueprint; a container is a running instance of that image.

### Installation

Install Docker, verify it's running, and add your user to the docker group so you don't need `sudo` for every command.

```bash
sudo apt-get install docker.io -y
sudo systemctl status docker
sudo usermod -aG docker ubuntu
```

### Creating a Docker Image (Interactive Mode)

Here we create a Docker image by manually setting up a container (installing packages, configuring a web server) and then committing it. This is the "interactive" approach — useful for learning, but in production you'd use a Dockerfile instead.

**What we're doing:** Start a bare Ubuntu container, manually install a web server inside it, serve a custom page, then save the whole thing as a reusable image. Think of it as taking a snapshot of a configured machine.

```bash
# Remove all existing containers (running or stopped) to start clean
# ls -aq lists all container IDs quietly; xargs passes them to rm -f (force remove)
docker container ls -aq | xargs docker container rm -f

# Start a new container named "test" from Ubuntu 16.04 image
# -it = interactive + TTY (gives you a shell inside the container)
docker container run --name test -it ubuntu:16.04

# --- Now you're INSIDE the container's shell ---

# Update package lists (container starts with no cached package info)
apt-get update

# Install curl (HTTP client), git, and apache2 (web server)
apt-get install curl git apache2 -y

# Create a custom homepage for Apache
echo "welcome to the world of docker and kubernetes" > /var/www/html/index.html

# Start Apache in the foreground (keeps the container running)
# -D FOREGROUND prevents Apache from daemonizing, which would exit the container
apachectl -D foreground

# Press Ctrl+P, Ctrl+Q to detach from the container without stopping it
```

### Inspect and Test

Use `inspect` to get the container's IP address, then `curl` to verify the web server is responding.

```bash
# Show detailed container metadata (network settings, mounts, state, etc.)
# Look for "IPAddress" in the output to find the container's IP on the bridge network
docker container inspect test

# Test that Apache is serving your custom page (replace <container_ip> with the actual IP)
curl -v http://<container_ip>:80
```

### Commit Container to Image

After making changes inside a running container, you can save (commit) it as a new image. The `--change` flag lets you set the startup command and exposed ports so the image works correctly when run later.

```bash
# Stop the container first (commit works on stopped containers too)
docker container stop test

# Save the container's current filesystem as a new image named "web:httpdv2"
# --change='CMD [...]' sets the default command when the image is run later
#   Without this, the image would default to /bin/bash and exit immediately
# -c "EXPOSE 80" documents that this image listens on port 80
docker container commit --change='CMD ["apachectl","-DFOREGROUND"]' -c "EXPOSE 80" test web:httpdv2

# Run a new container from your freshly created image
# -d = detached (runs in background)
# -P = publish all exposed ports to random host ports (e.g., 80 → 32768)
docker container run --name webhttp -d -P web:httpdv2
```

### Push to Docker Hub

Docker Hub is a public registry for sharing images. You must tag your image with your Docker ID before pushing, since only you can push to your own namespace.

```bash
# Authenticate with Docker Hub
docker login docker.io -u <username>

# Tag the image with your Docker ID namespace (required for push permissions)
# Format: registry/username/image:tag
docker image tag web:httpdv2 docker.io/<dockerid>/web:httpdv2

# Upload the image to Docker Hub so others (or other machines) can pull it
docker image push docker.io/<dockerid>/web:httpdv2
```

> **⚠️ Common Mistakes:**
> - Forgetting `CMD` when committing — container exits immediately because it defaults to `/bin/bash`
> - Not tagging with your Docker ID before pushing — you'll get "permission denied"
> - Using `-it` (interactive) when you want `-d` (detached/background)

> **✅ Key Takeaway:** The interactive commit approach teaches you what's inside an image. In real projects, always use a `Dockerfile` for reproducibility.

---

## Docker Networking

> **Why this matters:** Understanding Docker networking is essential because Kubernetes networking builds on the same concepts (IP per pod, port mapping, network isolation).

Docker creates virtual networks to allow containers to communicate. By default, containers are attached to a `bridge` network and get their own IP address. The `-P` flag maps container ports to random host ports so you can access services from outside.

```
+--------------------------------------------------+
|  Host (e.g., 192.168.1.10)                       |
|                                                  |
|  0.0.0.0:32768 ---+                              |
|                    |                             |
|  +--- bridge network (172.17.0.0/16) ---------+  |
|  |                 |                           | |
|  |  nginx container <--+                       | |
|  |  172.17.0.2:80                              | |
|  |                                             | |
|  +---------------------------------------------+ |
+--------------------------------------------------+

-P flag: maps container port to a random host port (32768+)
External access: http://host-ip:32768 --> nginx:80
```

```bash
docker container run -d -P nginx:latest
docker network ls
docker network inspect bridge
docker container inspect <container_name>
```

---

## Setting Up a Local Cluster with Kind

> **Why this matters:** You need a Kubernetes cluster to practice pods, deployments, and all other concepts. Kind (Kubernetes in Docker) lets you run a full Kubernetes cluster locally using Docker — perfect for learning without needing cloud resources.

### What is Kind?

**Kind** (Kubernetes in Docker) is a tool for running local Kubernetes clusters using Docker containers as "nodes". Each Kubernetes node in your cluster is actually a Docker container running a full Kubernetes environment.

**How it works:**
- Kind creates Docker containers that act as Kubernetes nodes
- These containers run Kubernetes components (API server, etcd, scheduler, kubelet, etc.)
- You interact with the cluster using standard `kubectl` commands
- The cluster runs on your local machine using Docker

**What it does:**
- Creates lightweight Kubernetes clusters for development/testing
- Supports single-node and multi-node clusters
- Provides a production-like Kubernetes environment locally
- Automatically configures kubectl to access the cluster
- Can be created and destroyed in minutes

**Benefits:**
- Lightweight — runs as Docker containers
- Fast cluster creation (seconds to minutes)
- Multi-node clusters supported
- Cross-platform (Linux, macOS, Windows)
- No cloud costs
- Uses standard Kubernetes (not a stripped-down version)

### Alternatives to Kind

| Tool | How it works | Pros | Cons | Best for |
|------|-------------|------|------|----------|
| **Kind** | Docker containers as nodes | Fast, lightweight, multi-node, cross-platform | Requires Docker | Dev, testing, learning |
| **Minikube** | VM or container as single node | Mature, feature-rich, many add-ons | Heavier resource usage, single-node by default | Beginners, full K8s features |
| **k3d** | Docker containers running k3s | Very fast, lightweight, k3s is smaller | Uses k3s (lightweight K8s, not full K8s) | Resource-constrained environments |
| **MicroK8s** | Native Linux installation | Lightweight, fast, no VM overhead | Linux only, requires snap | Linux desktops, edge devices |
| **Docker Desktop** | Built-in single-node cluster | Easy if you already use Docker Desktop | Limited features, single-node only | Quick testing on Mac/Windows |
| **K3s** | Lightweight Kubernetes binary | Very small footprint, edge-optimized | Not full K8s, manual setup | IoT, edge, resource-constrained |
| **kubeadm** | Bootstrap cluster on existing nodes | Production-grade, full control | Complex setup, multiple machines needed | Production clusters |

**Why Kind for learning?**
- It's lightweight and fast
- Supports multi-node clusters (important for learning scheduling, high availability)
- Uses standard Kubernetes (not a stripped-down version like k3s)
- Cross-platform support
- Easy to create/destroy clusters
- Actively maintained by Kubernetes SIGs

### Installation

**macOS (using Homebrew):**
```bash
brew install kind
```

**Linux:**
```bash
curl -Lo ./kind "https://kind.sigs.k8s.io/dl/v0.20.0/kind-linux-amd64"
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind
```

**Windows (using Chocolatey):**
```bash
choco install kind
```

### Verify Installation

```bash
kind version
```

### Create a Single-Node Cluster

```bash
# Create a cluster named "learning"
kind create cluster --name learning

# Verify cluster is running
kubectl cluster-info
kubectl get nodes
```

**What you'll see:**
- `cluster-info` shows Kubernetes master is running
- `get nodes` shows one node named `learning-control-plane`

### Cluster Management Commands

```bash
# List all kind clusters
kind get clusters

# Get cluster kubeconfig (for accessing the cluster)
kind get kubeconfig --name learning

# Delete a cluster
kind delete cluster --name learning
```

### Accessing the Cluster

Kind automatically configures `kubectl` to use your new cluster. The kubeconfig is merged into your `~/.kube/config` file.

```bash
# Check current context
kubectl config current-context

# List all contexts
kubectl config get-contexts

# Switch contexts (if you have multiple clusters)
kubectl config use-context kind-learning
```

> **Gotcha:** If you have multiple clusters, always check which context you're using. Commands affect the currently selected cluster.

### Troubleshooting

**Cluster won't start:**
```bash
# Check Docker is running
docker ps

# Check Kind logs
kind export logs --name learning
```

**kubectl can't connect:**
```bash
# Verify context is set correctly
kubectl config current-context

# Reset kubeconfig
kind export kubeconfig --name learning
```

### Cleanup When Done

```bash
# Delete the cluster to free resources
kind delete cluster --name learning
```

---

## Pod Basics

> **Why this matters:** Everything in Kubernetes revolves around Pods. Every container you run, every app you deploy — it's all running inside a Pod. This is the fundamental building block you'll use in every single section that follows.

A Pod is the smallest deployable unit in Kubernetes. It represents one or more containers that share the same network namespace (same IP address) and storage. In most cases, a pod runs a single container. Pods are ephemeral — if they die, they're gone unless managed by a controller like a ReplicaSet or Deployment.

```
┌──────── Pod ────────┐
│  ┌───────────────┐  │
│  │   Container   │  │  ← Usually one container per pod
│  │   (nginx)     │  │
│  └───────────────┘  │
│                     │
│  IP: 10.244.1.5     │  ← Pod gets its own IP
│  Volumes: [data]    │  ← Shared storage
└─────────────────────┘
```

**Mental model:** Think of a Pod as a "logical host" — like a tiny VM that runs your app. Containers in the same pod are like processes on the same machine.

### Common Commands

These are the essential kubectl commands for creating, inspecting, and managing pods.

```bash
# List pods
kubectl get pod
kubectl get pod --all-namespaces
kubectl get pod -o wide

# Create a pod
kubectl run testpod --image=nginx --port=80

# Inspect
kubectl describe pod testpod
kubectl get pod testpod -o yaml
kubectl get pod testpod -o json

# Exec into pod
kubectl exec -it testpod -- sh

# Delete
kubectl delete pod testpod
```

### Create Pod from YAML

Instead of using imperative commands, you can define pods declaratively in YAML files. The `--dry-run=client -o yaml` trick generates the YAML without actually creating the resource — useful for creating templates.

```bash
# Generate YAML with dry-run
kubectl run demopod --image=nginx --dry-run=client -o yaml > demopod.yaml

# Apply
kubectl apply -f demopod.yaml

# Delete
kubectl delete -f demopod.yaml
```

> **💡 Pro Tip:** Always use `--dry-run=client -o yaml` to generate YAML templates. Never write them from scratch — it's error-prone and slow. Edit the generated file to add what you need.

> **⚠️ Gotcha:** Pods created directly (without a controller) won't be recreated if they crash or the node goes down. In production, always use Deployments or StatefulSets.

### Hands-On Lab: Pod Basics

**Prerequisites:** Complete the [Setting Up a Local Cluster with Kind](#setting-up-a-local-cluster-with-kind) section first to have a running cluster.

#### Lab Setup

If you haven't already, create your Kind cluster:

```bash
# Create a cluster named "learning"
kind create cluster --name learning

# Verify cluster is running
kubectl cluster-info
kubectl get nodes
```

#### Exercise 1: Create Your First Pod

```bash
# Create a simple nginx pod
kubectl run my-first-pod --image=nginx --port=80

# Check pod status
kubectl get pod

# Watch the pod come up (press Ctrl+C to stop watching)
kubectl get pod -w
```

**What to observe:**
- Status transitions: `ContainerCreating` → `Running`
- Each pod gets a unique name (if not specified)
- The `READY` column shows `1/1` (1 container ready out of 1 total)

#### Exercise 2: Inspect the Pod

```bash
# Get detailed information about the pod
kubectl describe pod my-first-pod

# View the pod's YAML configuration
kubectl get pod my-first-pod -o yaml

# View the pod's JSON configuration
kubectl get pod my-first-pod -o json
```

**What to observe in `describe` output:**
- `Name`, `Namespace`, `Node` (which node it's running on)
- `Labels` (key-value pairs for organization)
- `Containers` section with image, ports, resources
- `Events` section (pod lifecycle events)

#### Exercise 3: Pod Logs

```bash
# View logs from the pod
kubectl logs my-first-pod

# Follow logs in real-time (like tail -f)
kubectl logs -f my-first-pod

# If pod has multiple containers, specify which one
kubectl logs my-first-pod -c <container-name>
```

#### Exercise 4: Exec into Pod

```bash
# Get an interactive shell inside the pod
kubectl exec -it my-first-pod -- sh

# Inside the pod, try these commands:
# - ls -la (list files)
# - ps aux (see running processes)
# - cat /etc/os-release (see OS info)
# - nginx -v (check nginx version)
# - exit (to leave the pod)
```

#### Exercise 5: Create Pod from YAML

```bash
# Generate YAML template without creating the pod
kubectl run yaml-pod --image=nginx --port=80 --dry-run=client -o yaml > yaml-pod.yaml

# View the generated YAML
cat yaml-pod.yaml

# Edit the YAML to add a label (optional)
# Add under metadata.labels:
#   app: nginx
#   env: learning

# Create the pod from YAML
kubectl apply -f yaml-pod.yaml

# Verify it was created
kubectl get pod yaml-pod
```

#### Exercise 6: Pod Networking

```bash
# Get the pod's IP address
kubectl get pod yaml-pod -o wide

# Exec into the pod and test connectivity
kubectl exec -it yaml-pod -- sh

# Inside the pod:
# - apt-get update && apt-get install curl -y
# - curl http://localhost (should return nginx welcome page)
# - curl http://<pod-ip> (should work too - same network namespace)
# - exit
```

#### Exercise 7: Delete Pods

```bash
# Delete a pod by name
kubectl delete pod my-first-pod

# Delete a pod using the YAML file
kubectl delete -f yaml-pod.yaml

# Delete all pods in the default namespace
kubectl delete pod --all
```

#### Cleanup

```bash
# If you used Kind, delete the cluster when done
kind delete cluster --name my-k8s-lab
```

**Key Takeaways from this lab:**
- Pods are ephemeral - delete them and they're gone
- Each pod gets its own IP address
- Pods can be created imperatively (`kubectl run`) or declaratively (YAML)
- `kubectl describe` is your best friend for debugging
- Pods without controllers won't auto-restart if they crash

---

## Pod Labels

> **Why this matters:** Labels are the "glue" of Kubernetes. Without labels, Services can't find pods, ReplicaSets can't manage pods, and you can't organize anything. Understanding labels is critical before moving to controllers.

Labels are key-value pairs attached to Kubernetes objects (like pods). They are used to organize, select, and filter resources. Labels are how Services find their pods, how ReplicaSets manage their pods, and how you can query specific groups of resources. They are mandatory for most controllers to work.

```
Pod: webserver-abc123          Pod: api-xyz789
+---------------------+       +---------------------+
| Labels:             |       | Labels:             |
|   app: nginx        |       |   app: flask        |
|   env: prod         |<--+   |   env: prod         |<--+
|   version: v2       |   |   |   version: v1       |   |
+---------------------+   |   +---------------------+   |
                           |                             |
        Service selector: env=prod  ---------------------+
        (selects BOTH pods)
```

```bash
# Create pod with label
kubectl run newpod --image=nginx -l app=nginx

# List labels
kubectl get pod --show-labels

# Filter by label
kubectl get pod -l app=tomee

# Add labels
kubectl label pod appserver env=dev version=1

# Overwrite label
kubectl label pod appserver app=web --overwrite

# Delete a label (note the minus sign at the end)
kubectl label pod demopod role-

# Delete all pods
kubectl delete pod --all
```

### Pod with Labels YAML

You can define multiple labels in the pod's metadata section. These labels can then be used by selectors in Services, ReplicaSets, and Deployments.

```yaml
apiVersion: v1
kind: Pod
metadata:
  labels:
    app: tomee
    env: dev
    version: v1
  name: demopod
spec:
  containers:
  - image: nginx
    name: demopod
```

> **Key Takeaway:** Use a consistent labeling strategy across your team. Common labels: `app` (application name), `env` (environment), `version` (release version), `tier` (frontend/backend).

---

## Namespaces

> **Why this matters:** In real clusters, multiple teams share the same cluster. Namespaces prevent them from stepping on each other's toes — separate resources, separate permissions, separate resource quotas.

Namespaces provide a way to divide cluster resources between multiple users or teams. They act as virtual clusters within a physical cluster. Resources in one namespace are isolated from resources in another. Kubernetes comes with default namespaces like `default`, `kube-system` (for system components), and `kube-public`.

```
+----------- Kubernetes Cluster -----------+
|                                          |
|  +- default --+  +- kube-system ------+  |
|  | your pods  |  | DNS, metrics       |  |
|  | your svcs  |  | system pods        |  |
|  +------------+  +--------------------+  |
|                                          |
|  +- team-a ---+  +- team-b -----------+  |
|  | their pods |  | their pods         |  |
|  | their svcs |  | their svcs         |  |
|  +------------+  +--------------------+  |
+------------------------------------------+
```

```bash
# List namespaces
kubectl get ns

# Create namespace
kubectl create ns test

# Describe
kubectl describe ns kube-system

# Generate YAML
kubectl create ns instavote --dry-run=client -o yaml > instavotens.yaml

# Apply
kubectl apply -f instavotens.yaml

# Run pod in a namespace
kubectl run testpod --image=nginx --port=80 -n instavote

# Interact with pod in namespace
kubectl get pod -n instavote
kubectl describe pod testpod -n instavote
kubectl exec -it testpod -n instavote -- bash
kubectl logs testpod -n instavote
kubectl delete pod testpod -n instavote

# Delete namespace (also deletes all resources inside it)
kubectl delete -f instavotens.yaml
```

> **Gotcha:** Deleting a namespace deletes EVERYTHING inside it — all pods, services, configmaps, secrets. There's no undo.

> **Pro Tip:** Always use `-n <namespace>` when working with non-default namespaces. Forgetting this is the #1 cause of "my pod doesn't exist" confusion.

---

## Volumes

> **Why this matters:** Containers lose all data when they restart. If your app writes to disk (databases, uploads, logs), you NEED volumes. Choosing the wrong volume type is a common source of data loss.

Containers are ephemeral by default — any data written inside a container is lost when it restarts or is deleted. Volumes solve this by providing persistent or shared storage that outlives the container. Kubernetes supports many volume types, each with different persistence guarantees.

| Volume Type | Persists after pod delete? | Survives node failure? | Use case |
|-------------|---------------------------|----------------------|----------|
| `emptyDir` | No | No | Temp files, inter-container sharing |
| `hostPath` | Yes (on that node) | No | Single-node dev/testing |
| `PVC` | Yes | Yes | Production databases, stateful apps |

### emptyDir (Ephemeral)

An `emptyDir` volume is created when a pod is assigned to a node and exists as long as the pod runs on that node. When the pod is deleted, the data is permanently lost. It's useful for scratch space or sharing files between containers in the same pod.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: web
  labels:
    tier: front
    app: nginx
    role: ui
spec:
  containers:
    - name: nginx
      image: nginx
      ports:
        - containerPort: 80
          protocol: TCP
      volumeMounts:
        - name: data
          mountPath: /var/www/html-sample-app
  volumes:
    - name: data
      emptyDir: {}
```

### hostPath (Persists on Node)

A `hostPath` volume mounts a file or directory from the host node's filesystem into the pod. Data persists even after the pod is deleted, but only on that specific node. If the pod is rescheduled to a different node, it won't find the data. Useful for single-node testing but not recommended for production multi-node clusters.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: web
  labels:
    tier: front
    app: nginx
    role: ui
spec:
  containers:
    - name: nginx
      image: nginx:stable-alpine
      imagePullPolicy: IfNotPresent
      ports:
        - containerPort: 80
          protocol: TCP
      volumeMounts:
        - name: data
          mountPath: /var/www/html-sample-app
  volumes:
    - name: data
      hostPath:
        path: /mnt/data
        type: DirectoryOrCreate
```

> **Gotcha:** If your pod gets rescheduled to a different node, it won't find the hostPath data. This is why hostPath is NOT suitable for production.

---

## Dynamic Storage Provisioning

> **Why this matters:** In production, you can't manually create disks for every database pod. Dynamic provisioning automates this — you just ask for storage and the cluster handles the rest.

In production, you don't want to manually create storage for every pod. Dynamic provisioning automatically creates a PersistentVolume (PV) when a PersistentVolumeClaim (PVC) is requested. A StorageClass defines how storage is provisioned (e.g., SSD, HDD, network storage). The PVC is a request for storage, and the PV is the actual provisioned storage that satisfies that request.

```
StorageClass ("local-path")
       |  provisions
       v
PVC ("I need 2Gi") ----> PV ("Here's 2Gi")
       |
       | mounted by
       v
Pod (/data/db) <-- data persists here
```

### PersistentVolumeClaim

A PVC requests a specific amount of storage with certain access modes. The cluster's StorageClass handles the actual provisioning.

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mongodb-pvc
spec:
  storageClassName: local-path
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
```

### Pod Using PVC

The pod references the PVC by name in its volumes section. The PV is only created once a pod actually claims the PVC.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: mongodb
spec:
  containers:
  - image: mongo
    name: mongodb
    volumeMounts:
    - name: mongodb-data
      mountPath: /data/db
    ports:
    - containerPort: 27017
      protocol: TCP
  volumes:
  - name: mongodb-data
    persistentVolumeClaim:
      claimName: mongodb-pvc
```

**Key points:**
- PV is created dynamically when a pod claims the PVC
- Deleting the pod does NOT delete the PV/PVC — data persists
- Deleting the PVC deletes the PV (with `Delete` reclaim policy)

**Lifecycle:**
```
Create PVC --> PVC Pending --> Create Pod --> PV Created & Bound --> Pod Running
Delete Pod --> PVC/PV still exist --> Recreate Pod --> Same data available
Delete PVC --> PV deleted --> Data gone forever
```

```bash
kubectl get sc
kubectl apply -f mongodb-pvc.yaml
kubectl apply -f mongodb-pod.yaml
kubectl get pvc
kubectl get pv
```

---

## Pod Resources & LimitRange

> **Why this matters:** Without resource limits, a single misbehaving pod can consume all CPU/memory on a node and crash everything else. Resource management is essential for cluster stability.

Kubernetes lets you specify how much CPU and memory each container needs. **Requests** are what the container is guaranteed to get — the scheduler uses this to decide which node to place the pod on. **Limits** are the maximum a container can use — if it exceeds memory limits, it gets killed (OOMKilled); if it exceeds CPU limits, it gets throttled.

```
CPU: 1000m = 1 full CPU core (250m = quarter core)
Memory: Mi = Mebibytes, Gi = Gibibytes

What happens when limits are exceeded:
  CPU limit exceeded    --> container is THROTTLED (slowed, not killed)
  Memory limit exceeded --> container is OOMKilled (killed and restarted)
```

### Setting Resource Requests and Limits

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: frontend
spec:
  containers:
  - name: app
    image: nginx
    resources:
      requests:
        memory: "64Mi"
        cpu: "250m"
      limits:
        memory: "128Mi"
        cpu: "500m"
```

### LimitRange (Enforce Constraints on a Namespace)

A LimitRange sets default, minimum, and maximum resource values for containers in a namespace. If a pod doesn't specify resources, it gets the defaults. If it requests more than the max or less than the min, it's rejected. This prevents any single pod from consuming too many resources.

```yaml
apiVersion: v1
kind: LimitRange
metadata:
  name: cpu-resource-constraint
spec:
  limits:
  - default:
      cpu: 500m
    defaultRequest:
      cpu: 500m
    max:
      cpu: "1"
    min:
      cpu: 100m
    type: Container
```

```bash
kubectl apply -f cpu-constraints.yaml
kubectl describe ns default
# Pods without explicit resources get defaults from LimitRange
# Pods exceeding max or below min will be rejected
```

> **Try this experiment:**
> 1. Apply the LimitRange above
> 2. Create a pod with `cpu: 50m` request — it will be **rejected** (below min of 100m)
> 3. Create a pod with `cpu: 2000m` limit — it will be **rejected** (above max of 1)
> 4. Create a pod with no resources specified — it gets the **defaults** (500m request, 500m limit)

---

## Pod Health Checks

> **Why this matters:** Without health checks, Kubernetes has no way to know if your app is actually working. A container can be "running" but completely broken (deadlocked, out of connections). Health checks let Kubernetes automatically detect and recover from these situations.

Kubernetes can automatically monitor your containers and take action when they become unhealthy. There are two main types of probes:
- **Liveness Probe** — checks if the container is alive. If it fails, Kubernetes restarts the container.
- **Readiness Probe** — checks if the container is ready to serve traffic. If it fails, the pod is removed from Service endpoints (no traffic is sent to it) but the container is NOT restarted.

```
Liveness Probe:  "Is the app alive?"    --> Fails = RESTART pod
Readiness Probe: "Can it serve traffic?" --> Fails = REMOVE from Service endpoints

Probe types: exec (run command), httpGet (HTTP request), tcpSocket (TCP connect)
```

### Liveness Probe (exec)

This example creates a file `/tmp/healthy`, then deletes it after 30 seconds. The liveness probe checks for this file every 5 seconds. Once the file is gone, the probe fails and Kubernetes restarts the container.

```yaml
apiVersion: v1
kind: Pod
metadata:
  labels:
    test: liveness
  name: liveness-exec
spec:
  containers:
  - name: liveness
    image: k8s.gcr.io/busybox
    args:
    - /bin/sh
    - -c
    - touch /tmp/healthy; sleep 30; rm -rf /tmp/healthy; sleep 600
    livenessProbe:
      exec:
        command:
        - cat
        - /tmp/healthy
      initialDelaySeconds: 5
      periodSeconds: 5
```

**Watch it in action:**
```bash
kubectl apply -f pod_liveness.yaml
# Watch the pod — after ~35 seconds, RESTARTS column will increment
kubectl get pod -w
# See the probe failure events
kubectl describe pod liveness-exec
```

### Liveness + Readiness Probes (httpGet)

HTTP probes are more common in production. The liveness probe hits `/healthy` and the readiness probe hits `/ready`. The `initialDelaySeconds` gives the app time to start up before probing begins. `failureThreshold` defines how many consecutive failures trigger the action.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: kuard
  labels:
    app: kuard
spec:
  volumes:
    - name: "kuard-data"
      emptyDir: {}
  containers:
    - image: gcr.io/kuar-demo/kuard-amd64:1
      name: kuard
      ports:
        - containerPort: 8080
          name: http
          protocol: TCP
      resources:
        requests:
          cpu: "500m"
          memory: "128Mi"
        limits:
          cpu: "1000m"
          memory: "256Mi"
      volumeMounts:
        - mountPath: "/data"
          name: "kuard-data"
      livenessProbe:
        httpGet:
          path: /healthy
          port: 8080
        initialDelaySeconds: 5
        timeoutSeconds: 1
        periodSeconds: 10
        failureThreshold: 3
      readinessProbe:
        httpGet:
          path: /ready
          port: 8080
        initialDelaySeconds: 30
        timeoutSeconds: 1
        periodSeconds: 10
        failureThreshold: 3
```

---

## ConfigMaps & Secrets

> **Why this matters:** You should NEVER hardcode configuration (database URLs, API keys) in your container images. ConfigMaps and Secrets let you separate configuration from code, so the same image works in dev, staging, and production — just swap the config.

Applications often need configuration (database URLs, feature flags, etc.) that varies between environments. Hardcoding these values in container images is bad practice. Kubernetes provides two resources to inject configuration into pods:
- **ConfigMap** — stores non-sensitive configuration as key-value pairs. Can be injected as environment variables or mounted as files.
- **Secret** — stores sensitive data (passwords, tokens, keys) in base64-encoded format. Works the same way as ConfigMaps but with additional access controls.

```
ConfigMap (non-sensitive)        Secret (sensitive)
  DB_HOST=mysql                   DB_PASS=cGFzcw== (base64)
  DB_PORT=3306                    API_KEY=a2V5MTIz
       |                               |
       v                               v
+------------- Pod ---------------------------------+
|  env: DB_HOST=mysql       (from ConfigMap)        |
|  env: DB_PASS=pass        (from Secret, decoded)  |
|  /etc/config/db.conf      (mounted as file)       |
+---------------------------------------------------+
```

### ConfigMap

```bash
# Create from YAML
kubectl apply -f multimap.yml

# Inspect
kubectl get cm
kubectl describe cm multimap
kubectl get cm multimap -o yaml
```

**Inject as volume mount** — ConfigMap data appears as files inside the container at the specified mount path:
```bash
kubectl apply -f cmpod.yml
kubectl exec -it cmvol -- sh
cd /etc/name
# Files here contain ConfigMap data
```

**Inject as environment variables** — ConfigMap values become environment variables inside the container:
```bash
kubectl apply -f envpod.yml
kubectl exec -it envpod -- sh
env  # See injected variables
```

### Secrets

Secrets work like ConfigMaps but are intended for sensitive data. Values are stored as base64-encoded strings (not encrypted by default — just encoded). When mounted into a pod, they appear as plain text files.

```bash
# Decode base64 value
echo "bmlnZWxwb3VsdG9u" | base64 --decode

# Create secret
kubectl apply -f tkb-secret.yml

# Use in pod
kubectl apply -f secretpod.yml
kubectl exec -it secret-pod -- sh
cd /etc/tkb
cat username
cat password
```

> **Security Warning:** Kubernetes Secrets are only base64-encoded, NOT encrypted. Anyone with namespace access can read them. For real security, use HashiCorp Vault or enable encryption at rest in etcd.

---

## Multi-Container Pods

> **Why this matters:** The sidecar pattern (log shippers, service meshes, file syncing) is used everywhere in production Kubernetes. Understanding how containers share resources within a pod is essential.

Sometimes you need multiple containers working together as a single unit. Containers in the same pod share:
- **Network** — same IP address, can communicate via `localhost`
- **Volumes** — can mount the same volumes to share files

Common patterns include:
- **Sidecar** — a helper container that enhances the main container (e.g., log shipping, syncing files)
- **Ambassador** — a proxy container that handles external communication
- **Adapter** — a container that transforms output from the main container

In this example, an nginx container serves files while a sync container populates them — both sharing the same volume.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: web
  labels:
    tier: front
    app: nginx
    role: ui
spec:
  containers:
    - name: nginx
      image: nginx:stable-alpine
      ports:
        - containerPort: 80
          protocol: TCP
      volumeMounts:
        - name: data
          mountPath: /var/www/html-sample-app

    - name: sync
      image: schoolofdevops/sync:v2
      volumeMounts:
        - name: data
          mountPath: /var/www/app

  volumes:
    - name: data
      emptyDir: {}
```

```bash
# Exec into specific container (use -c flag)
kubectl exec -it web -c nginx -- sh
kubectl exec -it web -c sync -- sh

# View logs per container
kubectl logs web -c nginx
kubectl logs web -c sync
```

> **Prove they share network & storage:**
> 1. Exec into nginx: run `hostname` and `ip a` — note the IP
> 2. Exec into sync: run `hostname` and `ip a` — same hostname, same IP!
> 3. Create files in nginx at `/var/www/html-sample-app/` — they appear in sync at `/var/www/app/`

---

## Multi-Node Cluster with Kind

> **Why this matters:** A single-node cluster hides important concepts: pod scheduling across nodes, node failures, network policies between nodes. Kind gives you a realistic multi-node cluster on your laptop in under 2 minutes.

Minikube runs a single-node cluster, which is fine for learning basics. But to understand how Kubernetes schedules pods across nodes, handles node failures, and distributes workloads, you need a multi-node cluster. **Kind** (Kubernetes IN Docker) creates multi-node clusters locally by running each "node" as a Docker container. It's lightweight and fast compared to spinning up VMs.

```
+--- Your Machine (Docker) -------------------------+
|                                                   |
|  control-plane     worker-1    worker-2  worker-3 |
|  (API server,     (runs pods) (runs pods)(runs)   |
|   scheduler,                                      |
|   etcd)           Each "node" is a Docker         |
|                   container!                      |
+---------------------------------------------------+
```

### Install Kind

```bash
sudo apt-get update
sudo apt-get install docker.io -y

# Download kind (AMD64)
[ $(uname -m) = x86_64 ] && curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.31.0/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind
```

### Cluster Config

This configuration creates a cluster with 1 control plane node and 4 worker nodes. The control plane runs the API server, scheduler, and controller manager. Worker nodes run your application pods.

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
- role: worker
- role: worker
- role: worker
- role: worker
```

### Create Cluster

```bash
kind create cluster --name my-cluster --config cluster-config.yaml

# Install kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# Verify
kubectl get nodes
```

---

## ReplicaSets

> **Why this matters:** In production, you never run a single pod. If it crashes, your app is down. ReplicaSets ensure multiple copies of your pod are always running — if one dies, another is created instantly. This is how Kubernetes provides high availability.

A ReplicaSet ensures that a specified number of identical pod replicas are running at any given time. If a pod crashes or is deleted, the ReplicaSet automatically creates a new one to maintain the desired count. It uses label selectors to identify which pods it manages — any pod matching the selector is counted toward the replica count.

```
ReplicaSet: vote (desired: 3, selector: role=vote)

  +-----+  +-----+  +-----+
  |vote |  |vote |  |vote |   <-- 3 running = desired state
  |:v2  |  |:v2  |  |:v2  |
  +-----+  +-----+  +-----+

  Delete one pod --> RS immediately creates a replacement
  Create pod with matching label --> RS may terminate it (already at desired count)
```

**Limitations:** ReplicaSets don't support rolling updates. If you change the pod template (e.g., update the image), existing pods are NOT affected. You must manually delete old pods for new ones to be created with the updated spec. For automatic rolling updates, use Deployments instead.

### ReplicaSet YAML

The `selector` defines which pods this ReplicaSet manages. `matchLabels` requires exact matches, while `matchExpressions` allows more complex logic (In, NotIn, Exists, DoesNotExist). The `template` is the blueprint for creating new pods.

```yaml
apiVersion: apps/v1
kind: ReplicaSet
metadata:
  name: vote
spec:
  replicas: 8
  minReadySeconds: 20
  selector:
    matchLabels:
      role: vote
    matchExpressions:
      - {key: version, operator: In, values: [v1, v2, v3]}
  template:
    metadata:
      name: vote
      labels:
        app: python
        role: vote
        version: v2
    spec:
      containers:
        - name: app
          image: schoolofdevops/vote:v2
          ports:
            - containerPort: 80
              protocol: TCP
```

### Commands

```bash
# Create
kubectl apply -f vote-rs.yaml

# List ReplicaSets and their pods
kubectl get rs
kubectl get pod
kubectl describe rs vote

# Scale up or down
kubectl scale rs vote --replicas=6

# Test high availability — delete a pod and watch it recreate
kubectl delete pod <pod_name>
```

### Updating Images in ReplicaSet

After updating the image in the YAML and applying:

```bash
kubectl apply -f vote-rs.yaml
```

> **Important:** ReplicaSet does NOT automatically update existing pods. You must manually delete old pods for new ones to be created with the updated image. Use **Deployments** for rolling updates.

---

## Deployments

> **Why this matters:** This is what you'll use 90% of the time in production. Deployments give you everything ReplicaSets do PLUS zero-downtime updates and instant rollbacks. If you only learn one controller, make it this one.

A Deployment is a higher-level controller that manages ReplicaSets and provides declarative updates to pods. It solves the key limitation of ReplicaSets — when you update the pod template (e.g., change the image), the Deployment automatically performs a **rolling update**, gradually replacing old pods with new ones without downtime. It also maintains a revision history so you can rollback to any previous version.

```
Deployment: vote
  |
  +--> RS v2 (current, 4 pods)    RS v1 (old, 0 pods, kept for rollback)
  |
  | Update image to v3:
  +--> RS v3 (scaling up)         RS v2 (scaling down)
  |
  | Complete:
  +--> RS v3 (4 pods)             RS v2 (0 pods, kept for rollback)
```

### Deployment Strategy

- **RollingUpdate** — gradually replaces pods. `maxSurge` controls how many extra pods can be created during the update. `maxUnavailable` controls how many pods can be down during the update.
- **Recreate** — kills all old pods first, then creates new ones (causes downtime).

### Deployment YAML

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vote
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 2
      maxUnavailable: 0
  revisionHistoryLimit: 4
  paused: false
  replicas: 4
  minReadySeconds: 20
  selector:
    matchLabels:
      role: vote
    matchExpressions:
      - {key: version, operator: In, values: [v1, v2, v3]}
  template:
    metadata:
      name: vote
      labels:
        app: python
        role: vote
        version: v2
    spec:
      containers:
        - name: app
          image: schoolofdevops/vote:v2
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 80
              protocol: TCP
```

### Create and Inspect

```bash
kubectl apply -f vote-deploy.yaml

# List deployment, replicaset, and pods together
kubectl get deployment,rs,pod

kubectl get deployment
kubectl get rs
kubectl get pod
```

### Scaling

You can scale by editing the YAML or using the command line:

```bash
# Edit the file and change replicas, then apply
vi vote-deploy.yaml
kubectl apply -f vote-deploy.yaml

# Or scale directly
kubectl scale deployment vote --replicas=4
```

### Rolling Update

To trigger a rolling update, change the image version in the YAML and apply:

```bash
vi vote-deploy.yaml
# Change image to schoolofdevops/vote:v4 and label to v3
kubectl apply -f vote-deploy.yaml

# Watch the rollout progress
kubectl rollout status deployment vote

# Verify pods are updated
kubectl get pod
kubectl describe pod <pod_name>
```

### Rollback

Deployments maintain a revision history. You can inspect and rollback to any previous version:

```bash
# View rollout history
kubectl rollout history deployment vote

# View details of a specific revision
kubectl rollout history deployment vote --revision=1

# Rollback to a specific revision
kubectl rollout undo deployment vote --to-revision=1

# Verify
kubectl rollout status deployment vote

# Add annotation to track change reason
kubectl annotate deployment vote "kubernetes.io/change-cause=image version is vote:v2"
```

### Delete

```bash
kubectl delete deployment vote
# or
kubectl delete -f vote-deploy.yaml
```

---

## Services

> **Why this matters:** Pods get random IPs that change every time they restart. Without Services, your frontend would need to track every backend pod IP manually. Services provide a stable "front door" that never changes, no matter how many pods come and go behind it.

A Service provides a stable network endpoint (IP and DNS name) to access a set of pods. Pods are ephemeral — they come and go, and their IPs change. A Service solves this by providing a fixed address that automatically routes traffic to healthy pods matching its selector. It acts as an internal load balancer, distributing requests across all matching pod replicas.

```
Client Pod                        Service: votesvc
                                  ClusterIP: 10.96.0.15
curl votesvc:80  ------>          Selector: role=vote
                                       |
                            +----------+----------+
                            |          |          |
                          Pod A      Pod B      Pod C
                         10.244.1.5 10.244.2.3 10.244.3.7
```

### Service Types

| Type | Description |
|------|-------------|
| **ClusterIP** | Default. Accessible only within the cluster. |
| **NodePort** | Exposes the service on a static port on each node's IP. Accessible from outside the cluster. |
| **LoadBalancer** | Provisions an external load balancer (cloud or MetalLB). Gets an external IP. |

### ClusterIP Service (Default)

Create a service that exposes a ReplicaSet internally within the cluster:

```bash
# Create ReplicaSets for testing
kubectl apply -f webrs.yaml
kubectl apply -f vote-rs.yaml

# Expose the vote ReplicaSet as a ClusterIP service
kubectl expose rs vote --name votesvc --selector role=vote --port=80

# Check service
kubectl get svc
kubectl describe svc votesvc
```

The `describe` output shows **Endpoints** — these are the pod IPs that the service routes traffic to. They match the pods selected by the service's selector.

### Service DNS and Discovery

Kubernetes runs an internal DNS server. Every service gets a DNS entry. Pods can reach services by name instead of IP.

```bash
# Login to the web pod
kubectl exec -it <web-pod-name> -- bash

# Access the vote service by name
curl -v http://votesvc:80

# Inspect DNS resolution
apt-get update && apt-get install dnsutils -y
nslookup votesvc

# Check DNS config
cat /etc/resolv.conf

exit
```

### Service Load Balancing

When you scale up replicas, the service automatically discovers new pods and load-balances across all of them:

```bash
# Scale vote to 4 replicas
kubectl scale rs vote --replicas=4

# Verify endpoints updated
kubectl describe svc votesvc

# Login to web pod and test — repeat curl multiple times
kubectl exec -it <web-pod-name> -- bash
curl -v http://votesvc:80
# Each request may hit a different pod
exit
```

### Cross-Namespace Communication

Services are scoped to a namespace. To reach a service in a different namespace, use `<service-name>.<namespace>`:

```bash
# Create a namespace and deploy there
kubectl create ns ecom
kubectl apply -f vote-rs.yaml -n ecom
kubectl expose rs vote --name votesvc --selector role=vote --port=80 -n ecom

# From a pod in the default namespace:
kubectl exec -it <web-pod-name> -- bash

# This FAILS (different namespace)
curl -v http://votesvc:80

# This WORKS (fully qualified with namespace)
curl -v http://votesvc.ecom:80

# DNS lookup
nslookup votesvc.ecom

exit
```

**Rule:**
- Same namespace → use `servicename`
- Different namespace → use `servicename.namespace`

---

## Service Type: NodePort

A NodePort service exposes your application on a static port (30000–32767) on every node's IP address. This allows external access without a load balancer — useful for development and testing.

### NodePort Service YAML

```yaml
apiVersion: v1
kind: Service
metadata:
  name: vote
  labels:
    role: vote
spec:
  selector:
    role: vote
  ports:
    - port: 80
      targetPort: 80
      nodePort: 30000
  type: NodePort
```

### Create and Access

```bash
kubectl apply -f vote-svc.yaml

# Check service
kubectl get svc

# Get node IP
kubectl get node -o wide

# Access the application
curl http://<node-ip>:30000
# Or open http://<node-ip>:30000 in a browser
```

---

## Service Type: LoadBalancer

In cloud environments, a LoadBalancer service automatically provisions an external load balancer with a public IP. For bare-metal or local clusters (like kind), you need **MetalLB** to provide this functionality.

### Install MetalLB

MetalLB is a load balancer implementation for bare-metal Kubernetes clusters. It assigns external IPs to LoadBalancer services from a configured IP pool.

```bash
# Install MetalLB
kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/v0.13.7/config/manifests/metallb-native.yaml

# Check MetalLB pods are running
kubectl get pod -n metallb-system
```

### Configure IP Address Pool

Define the range of IPs MetalLB can assign. This should be in the same subnet as your nodes:

```bash
# Check your node IP range first
kubectl get node -o wide
```

```yaml
apiVersion: metallb.io/v1beta1
kind: IPAddressPool
metadata:
  name: first-pool
  namespace: metallb-system
spec:
  addresses:
  - 172.18.0.200-172.18.0.240
---
apiVersion: metallb.io/v1beta1
kind: L2Advertisement
metadata:
  name: example
  namespace: metallb-system
```

```bash
kubectl apply -f metallb-config.yaml
```

### Create LoadBalancer Service

```bash
# Create the deployment first
kubectl apply -f vote-deploy.yaml

# Expose as LoadBalancer
kubectl expose deployment vote --type=LoadBalancer --selector role=vote --port=80

# Check service — EXTERNAL-IP column will show the assigned IP
kubectl get svc

# Access the application using the external IP
curl http://<external-ip>:80
# Or open in browser
```

---

## StatefulSets

> **Why this matters:** Databases (MySQL, MongoDB, Kafka) need stable identities and persistent storage. Deployments treat pods as interchangeable — StatefulSets don't. If you're running any stateful workload, you need this.

A StatefulSet is like a Deployment but for stateful applications that need stable, unique network identities and persistent storage. Unlike Deployments where pods are interchangeable, StatefulSet pods get:
- **Stable hostnames** — pods are named `<statefulset-name>-0`, `<statefulset-name>-1`, etc.
- **Ordered deployment** — pods are created sequentially (0, 1, 2...) and terminated in reverse order.
- **Stable persistent storage** — each pod gets its own PVC that persists across rescheduling.

| Feature | Deployment | StatefulSet |
|---------|-----------|-------------|
| Pod names | Random (vote-abc123) | Ordered (web-0, web-1) |
| Storage | Shared PVC | Each pod gets own PVC |
| Scaling | All at once | One at a time, in order |
| Use case | Stateless apps | Databases, queues |

StatefulSets require a **Headless Service** (ClusterIP: None) which provides DNS entries for each individual pod.

### StatefulSet YAML

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx
  labels:
    app: nginx
spec:
  ports:
  - port: 80
    name: web
  clusterIP: None
  selector:
    app: nginx
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: web
spec:
  selector:
    matchLabels:
      app: nginx
  serviceName: "nginx"
  replicas: 3
  template:
    metadata:
      labels:
        app: nginx
    spec:
      terminationGracePeriodSeconds: 10
      containers:
      - name: nginx
        image: docker.io/library/nginx:latest
        ports:
        - containerPort: 80
          name: web
        volumeMounts:
        - name: www
          mountPath: /usr/share/nginx/html
  volumeClaimTemplates:
  - metadata:
      name: www
    spec:
      accessModes: [ "ReadWriteOnce" ]
      storageClassName: "standard"
      resources:
        requests:
          storage: 1Gi
```

### Create and Verify

```bash
kubectl apply -f webstatefulset.yaml

# Pods are created in order: web-0, web-1, web-2
kubectl get pod

# Delete a pod — it recreates with the SAME name (unlike Deployments)
kubectl delete pod web-0
kubectl get pod
```

### DNS Resolution with Headless Service

Each pod in a StatefulSet gets a DNS entry: `<pod-name>.<service-name>.<namespace>.svc.cluster.local`

```bash
# Login to a pod and test DNS
kubectl exec -it web-0 -- sh
apt-get update && apt-get install dnsutils iputils-ping -y
nslookup nginx
ping nginx
exit
```

### Cross-Pod Communication

Pods in a StatefulSet can communicate with each other and with other services using DNS:

```bash
# Create a test pod and service
kubectl run testpodsa --image=nginx --port=80
kubectl expose pod testpodsa --port=80

# From testpodsa, resolve the StatefulSet headless service
kubectl exec -it testpodsa -- sh
apt-get update && apt-get install dnsutils iputils-ping -y
nslookup nginx
ping nginx
exit

# From a StatefulSet pod, access the test service
kubectl exec -it web-0 -- sh
curl -v http://testpodsa
exit
```

### Delete StatefulSet

```bash
kubectl delete -f webstatefulset.yaml
```

---

## Pod Scheduling

> **Why this matters:** Not all nodes are equal. Some have GPUs, some have SSDs, some are in specific availability zones. Scheduling controls let you ensure pods land on the right nodes — and keep them off the wrong ones.

By default, the Kubernetes scheduler decides which node to place a pod on based on resource availability. But sometimes you need control over placement — for example, running GPU workloads on GPU nodes, or keeping pods off the control plane. Kubernetes provides several mechanisms for this.

```
Scheduling methods (most to least restrictive):
  nodeName           --> "Run on THIS specific node" (bypasses scheduler)
  nodeSelector       --> "Run on nodes with THESE labels"
  Taints/Tolerations --> "Keep pods OFF this node unless they tolerate it"
```

### nodeName (Direct Assignment)

Bypasses the scheduler entirely. The pod runs on the specified node regardless of resource constraints or taints.

```yaml
apiVersion: v1
kind: Pod
metadata:
  labels:
    app: tomee
    env: dev
    version: v1
  name: demopod
spec:
  containers:
  - image: nginx
    name: demopod
  nodeName: my-cluster-worker4
```

```bash
kubectl apply -f demopod.yaml
kubectl get pod -o wide
# Pod runs on the specified node
```

### Node Taints and Tolerations

Taints prevent pods from being scheduled on a node unless the pod has a matching toleration. The control plane node is tainted by default to prevent workload pods from running there.

```bash
# Check taints on control plane
kubectl describe node my-cluster-control-plane

# Remove the control plane taint (allows scheduling on it)
kubectl taint node my-cluster-control-plane node-role.kubernetes.io/control-plane-

# Verify taint removed
kubectl describe node my-cluster-control-plane
```

### nodeSelector (Label-Based Scheduling)

Assign labels to nodes, then use `nodeSelector` in the pod spec to constrain which nodes the pod can run on. The pod will only be scheduled on nodes matching ALL specified labels.

```bash
# Label nodes
kubectl label node my-cluster-worker cpu=gpu
kubectl label node my-cluster-worker2 cpu=gpu
kubectl label node my-cluster-worker3 disk=ssd

# Verify labels
kubectl get node --show-labels
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  labels:
    app: tomee
    env: dev
    version: v1
  name: demopod
spec:
  containers:
  - image: nginx
    name: demopod
  nodeSelector:
    cpu: gpu
```

```bash
kubectl apply -f demopod.yaml
kubectl get pod -o wide
# Pod runs only on nodes with label cpu=gpu
```

### Node Maintenance (Drain & Uncordon)

When you need to perform maintenance on a node (OS updates, hardware repairs), you can drain it to safely evict all pods and prevent new scheduling.

```bash
# Drain a node — evicts all pods and marks it unschedulable
kubectl drain my-cluster-control-plane --force --ignore-daemonsets

# Verify node status (shows SchedulingDisabled)
kubectl get node

# New pods won't be scheduled on the drained node
kubectl create deployment testsched --image=nginx --replicas=4
kubectl get pod -o wide

# Bring node back online
kubectl uncordon my-cluster-control-plane
kubectl get node

# New pods can now be scheduled on the node again
kubectl create deployment testsched2 --image=nginx --replicas=4
kubectl get pod -o wide

# Cleanup
kubectl delete deployment testsched testsched2
```

---

## RBAC Policies

> **Why this matters:** By default, pods can't access the Kubernetes API. If your app needs to list pods, create secrets, or watch deployments (common in operators and CI/CD tools), you must explicitly grant permissions. RBAC is also how you restrict what team members can do in shared clusters.

Role-Based Access Control (RBAC) restricts what actions users, groups, or service accounts can perform in the cluster. By default, pods have limited API access. RBAC lets you grant fine-grained permissions using four resources:

```
RBAC Model:
  WHO                  WHAT CAN THEY DO         WHERE
  (ServiceAccount,     (Role/ClusterRole)       (Namespace/Cluster)
   User, Group)
                  connected by: RoleBinding / ClusterRoleBinding
```

- **Role** — defines permissions within a single namespace
- **ClusterRole** — defines permissions cluster-wide
- **RoleBinding** — grants a Role to a user/service account in a namespace
- **ClusterRoleBinding** — grants a ClusterRole cluster-wide

### Lab Setup

Clone and deploy the API Tester app which attempts to access various Kubernetes APIs:

```bash
git clone https://github.com/schoolofdevops/k8s-api-tester.git
cd k8s-api-tester

kubectl apply -f api-tester-deploy.yaml

# Check logs — you'll see access denied errors
kubectl get pods
kubectl logs -f api-tester-<pod-id>
```

### ServiceAccount

A ServiceAccount provides an identity for pods. By default, pods use the `default` service account which has minimal permissions.

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: api-tester
  namespace: default
```

### Role (Namespace-Scoped Permissions)

A Role grants permissions to list pods, services, and deployments within the `default` namespace:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: api-tester
  namespace: default
rules:
- apiGroups: [""]
  resources: ["pods", "services"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["apps", "extensions"]
  resources: ["deployments"]
  verbs: ["get", "list", "watch"]
```

### RoleBinding

Binds the Role to the ServiceAccount:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: api-tester
  namespace: default
subjects:
- kind: ServiceAccount
  name: api-tester
  namespace: default
roleRef:
  kind: Role
  name: api-tester
  apiGroup: rbac.authorization.k8s.io
```

### Apply and Assign to Deployment

```bash
kubectl apply -f api-tester-sa.yaml -f api-tester-role.yaml -f api-tester-rolebinding.yaml
```

Update the deployment to use the service account:

```yaml
spec:
  template:
    spec:
      serviceAccountName: api-tester
      containers:
      - name: api-tester
        image: docker.io/schoolofdevops/api-tester:latest
```

```bash
kubectl apply -f api-tester-deploy.yaml

# Verify — should now have access to pods, deployments, services
kubectl logs -f api-tester-<pod-id>
```

### ClusterRole & ClusterRoleBinding (Cluster-Wide Permissions)

For resources that are not namespace-scoped (like PersistentVolumes) or for access across all namespaces:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: api-tester-cluster-role
rules:
- apiGroups: [""]
  resources: ["persistentvolumeclaims"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
- apiGroups: [""]
  resources: ["persistentvolumes"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
```

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: api-tester-cluster-role-binding
subjects:
- kind: ServiceAccount
  name: api-tester
  namespace: default
roleRef:
  kind: ClusterRole
  name: api-tester-cluster-role
  apiGroup: rbac.authorization.k8s.io
```

```bash
kubectl apply -f api-tester-clusterrole.yaml -f api-tester-clusterrolebinding.yaml

# Verify — should now have access to PVs and PVCs
kubectl logs -f api-tester-<pod-id>
```

---

## Monitoring with Helm

> **Why this matters:** You can't fix what you can't see. Monitoring tells you when pods are crashing, nodes are running out of memory, or your app is slow. Helm makes deploying complex monitoring stacks (20+ YAML files) into a single command.

Helm is a package manager for Kubernetes. It bundles related Kubernetes manifests (Deployments, Services, ConfigMaps, etc.) into **charts** that can be installed, upgraded, and rolled back as a single unit. This lab uses Helm to deploy a full Prometheus + Grafana monitoring stack.

```
Without Helm: 20+ YAML files to manage manually
With Helm:    helm install prom prometheus-community/kube-prometheus-stack

What you get:
  Prometheus  --> collects metrics from all pods/nodes
  Grafana     --> visualizes dashboards
  Alertmanager --> sends alerts via email/slack
```

### Install Helm

```bash
curl https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 | bash

# Verify
helm --help
helm version
```

### Deploy Prometheus Stack

The `kube-prometheus-stack` chart includes Prometheus (metrics collection), Grafana (visualization), and Alertmanager (alerting) — all pre-configured to monitor your Kubernetes cluster.

```bash
# Add the Helm repository
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Download chart for inspection (optional)
cd ~
helm fetch --untar prometheus-community/kube-prometheus-stack
cd kube-prometheus-stack/
ls

# Deploy to a dedicated namespace
kubectl create ns monitoring
helm install prom -n monitoring prometheus-community/kube-prometheus-stack

# Validate
helm list -A
kubectl get all -n monitoring
kubectl get pods,svc -n monitoring
```

### Customize Grafana Access

Expose Grafana via NodePort so you can access it from a browser:

```bash
helm upgrade prom -n monitoring prometheus-community/kube-prometheus-stack \
  --set grafana.service.type=NodePort \
  --set grafana.service.nodePort=30200

# Verify
helm list -A
kubectl get svc -n monitoring
```

Access Grafana at `http://<node-ip>:30200`

**Default credentials:**
- User: `admin`
- Password: `prom-operator`

### Uninstall

```bash
helm list -A
helm uninstall -n monitoring prom
```

---

## Next Steps

- **Ingress** — HTTP routing, path-based routing, and TLS termination
- **DaemonSets** — run a pod on every node (log collectors, monitoring agents)
- **Jobs & CronJobs** — batch processing and scheduled tasks
- **Horizontal Pod Autoscaler** — auto-scale based on CPU/memory metrics
- **Network Policies** — control pod-to-pod traffic
