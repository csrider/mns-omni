# MessageNet Omni

Omni is an all-in-one messaging and communication device provided by MessageNet Systems as part of its *Connections* communication platform.

Omni represents one of my most significant professional achievements, as it is the first major successful combined-hardware/software product I've designed from the ground up and brought to market. There have since been more-refined versions released (versions I'm much more proud of than this rough prototype), but this early version is what I'm able to publish publicly, and it's better than nothing? :)

## What's Here

The repo here on Github is primarily an Android app I wrote to work within MessageNet's existing communication platform environment (which was written in C and utilizes AJAX, along with various other API-related goodies (like Node.js, etc.) that I could not publish publicly. This repo is not intended to be run or used in a development environment. Its intent is to showcase some of my work as part of my portfolio, and nothing else.

This repo consists of two major parts: the "demonstrator" prototype source code written in Java and XML, as well as some of the early/initial refactoring work to bring that to the first official product release version (all other resources stripped out).

## Features and Technologies

Here are some of the technologies employed and developed by me, in this product (details can be found throughout the source code):

- Android (app, device/hardware/electrical components, etc.)
	- App (activities, Room-DB/SQLite, fragments, services, threads, receivers, etc.)
	- Device/Hardware (Bluetooth/BLE, charging/electrical, camera, everything)
- Java (restricted to version 7 by supplier constraints)
- XML (internal Android, provisioning, TFTP, and some API features)
- AJAX / JSON (bulk of which is provided by the Connections platform)
- TFTP (used to initiate provisioning, remote configuration, and updates)
- Audio-Visual (video playback, video conferencing, sound effects, etc.)
- Socket-Server and stuff to support API