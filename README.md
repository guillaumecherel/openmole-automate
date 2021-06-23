# OpenMOLE Automate

OpenMOLE Automate helps you interact with an OpenMOLE instance programmatically.
It offers both an executable to send your simulation experiments
contained in a local folder to a running OpenMOLE instance, log their status and
retrieve their results.

## Executable build

Clone this repository:

```sh
$ git clone https://github.com/guillaumecherel/openmole-automate.git
```

Create the executable jar file with sbt:

```sh
$ sbt assembly
```

The executable is available at `target/scala-3.0.0/openmole-automate`. To use
it, run it from the root of your simulation experiment project and run it (see
below).




## Executable usage

In its simplest case, a simulation experiment for OpenMOLE consists of script
files (ending in `.oms`) and optionnaly additionnal resources such as data files
that are read by the scripts. When executed with OpenMOLE, they may create
output files containing the experiment results.

Put all script files and additionnal resource files in a folder named `job` at
the root of your project folder. This folder must contain every script and data
file that OpenMOLE will need to run the simulation experiment. Make sure that
all the output files go in a folder `output` also at the root of the project.

The folder `example-experiment/` is the root of a simple simulation experiment
that computes the numper Pi with Monte Carlo simulation. It illustrates how to
set up a simulation experiment with OpenMOLE Automate.

In that folder, `job/pi.oms` is the OpenMOLE script. There are no
additionnal resource files here. In this script file, `workDirectory` refers to
the directory from where you run OpenMOLE Automate, which must be your
simulation project root directory, here `example-experiment/`. The script
writes the result to the output file `workDirectory / "results/result.json"`,
which is resolved to `example-experiment/results/result.json`.

Make sure you have an OpenMOLE instance running in REST mode. You can start one
on localhost with docker:

```
$ docker run --rm --privileged -p 8080:8080 openmole/openmole:latest \
    openmole --port 8080 --reset --reset-password --rest --password ""
```

Put the following in the file `.openmole-automate.toml` at the root of your
project. There you can configure the paths. They are relative to the experiment
root folder, except `script`, which is relative to the `job` folder.

```
[Job]
job = "job/"
script = "pi.oms"
output = "output/"

[OpenMole]
address = "localhost"
port = "8080"
```

Move to the directory `example-experiment/` and run the OpenMOLE Automate 
executable:

```
../target/scala-3.0.0/openmole-automate
```

It will pack the folder `job/`, send it to the OpenMOLE instance at
`localhost:8080`, log the job status until it's done and download the results
to `output/`.




## Authentication

Not implemented yet.

Add this to `.openmole-automate.toml`

```
[[EgiAuthentication]]
certificate = "path/to/cert.p12"
vo = "vo.complex-systems.eu"

[[SshAuthentication]]
hostname = "host1"
login = "user1"
password = "pass1"

[[SshAuthentication]]
hostname = "host2"
login = "user2"
password = "pass2"
```



## Plugins

Not implemented yet.
