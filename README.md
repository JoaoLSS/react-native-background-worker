# react-native-background-worker

## Motivation

The react native community has some nice tools to work with background tasks, like [react-native-background-task](https://github.com/jamesisaac/react-native-background-task) and [react-native-background-fetch](https://github.com/transistorsoft/react-native-background-fetch), but those often offers some problems, as lack of maintenance, skipped tasks and so on. At the same time I liked so much the power and flexibility of WorkManager that I thought it would be awesome to bring those advantages into react native background tasks. So this is primarily a wrapper for the android work manager, with support for constrains, notification, persistence and much more, everything from the native side. For now this is heavily based on android's work manager and the react-native's headlessTask, apple has realeased BGTaskScheduler recently and I'm planning to look on that, but I sincerely don't know if this module could have a simmetric implementation on the iOS side

## Getting started

`$ npm install react-native-background-worker --save`

### Mostly automatic installation (for RN<0.60)

`$ react-native link react-native-background-worker`

### Manual installation

#### Android

1. Open up `android/app/src/main/java/[...]/MainApplication.java`
  - Add `import com.reactlibrary.BackgroundWorkerPackage;` to the imports at the top of the file
  - Add `new BackgroundWorkerPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-background-worker'
  	project(':react-native-background-worker').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-background-worker/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      implementation project(':react-native-background-worker')
  	```


## Basic Usage

##### You should create a drawable with the same name as the worker for the notification, if you do not do that, the app can crash!

```javascript
import bgWork, { useEnqueue, useWorker } from 'react-native-background-worker';

// To Register a Periodic Worker
const periodicId = await bgWork.setWorker({
    type: "periodic",
    name: "periodicWorker",
    notification: {
        title: "Periodic Worker",
        text: "MyApp is doing some periodic work in background"
    },
    workflow: async () => {
        //do work
    }
})

// To Register a Queued Worker
await bgWork.setWorker({
    type: "queued",
    name: "queuedWorker",
    notification: {
        title: "Queued Worker",
        text: "MyApp is doing some queued work in background"
    },
    workflow: async (payload: string) => {
        //do work
        return { result: "success", value: "some work has been done" }
    }
})

//To Enqueue some payload
const payloadId = await bgWork.enqueue({
    worker: "queuedWorker",
    payload: "some payload"
})

//To cancel enqueued payload or periodic workers
await bgWork.cancel(payloadId)

//To get some work's state at any moment
const periodicWorkerInfo = await bgWork.info(periodicId)

//To add a listener to state changes
const unsubscribe = bgWork.addListener(periodicId, (info) => console.log({ info }))

//To unsubscribe later
unsubscribe()

/**
 * HOOKS!
 * 
 * This API provides two hooks, the first is useEnqueue, it enqueues a payload and returns its state,
 * the second is useWorker, if the worker is periodic, it will return the work'state, if the worker
 * is queued, it will return the useEnqueue hook, linked with the worker's name.
*/

//To enqueue a payload and watch to it's state
const workInfo = useEnqueue({
	worker: "queuedWorker",
	payload: "some payload",
})

//To register a periodic worker and watch it's state
const periodicWorkerInfo = useWorker({
    type: "periodic",
    name: "periodicWorker",
    notification: {
        title: "Periodic Worker",
        text: "MyApp is doing some periodic work in background"
    },
    workflow: async () => {
        //do work
    }
},"detach")

//To register a periodic worker and receive a enqueue function
const enqueue = useWorker({
    type: "queued",
    name: "queuedWorker",
    notification: {
        title: "Queued Worker",
        text: "MyApp is doing some queued work in background"
    },
    workflow: async (payload: string) => {
        //do work
        return { result: "success", value: "some work has been done" }
    }
})

//That can ben used later to enqueue payloads to the worker that was setted
const anotherWorkInfo = enqueue("some payload")

```

## API

### bgWork

#### setWorker

```typescript
setWorker: (worker) => Promise<string|void>
```
Returns a promise that will resolve in a string been the id for a periodic worker and nothhing for the queued worker.
The only argument is worker, this is an object that contains:
```typescript
type:"periodic"|"queued"
```
The type of the worker
```typescript
name:string
```
The name of the worker
```typescript
notification: {
	title: string
	text: string
}
```
An object containing the title and text for the notification that will be displayed when the worker starts
```javascript
workflow: 
	() => Promise<void> //For periodic Worker
	(payload: any) => Promise<{ result:"success"|"failure"|"retry", value: any }> //For queued Worker
```
The workflow to be performed by this worker
```javascript
timeout?: number
```
This is the timeout in minutes the headlessTask will have to conclude, defaults to 10
```javascript
foregroundBehaviour?: "headlessTask" | "foreground" | "blocking"
```
This variable needs caution, it sets the behaviour of this worker when the app is in foreground. If this is set to headlessTask, the worker will start the headless service to execute the task, this could be necessary to long performing tasks that need to transition between app states, since in background async tasks tend to be a little unpredictable. It will also show the notification, since it is obliged to, this could be the reason why someone would choose the foreground mode, where the task is started as a normal async call, this will not show any notification, but could have an unpredictable behaviour if the app goes to background in the middle of the task. At last, the blocking behaviour is the default behaviour and, quoting the react native documentation, "This is to prevent developers from shooting themselves in the foot by doing a lot of work in a task and slowing the UI.", Since react is single threaded, the two other behaviours could make your UI sluggish, so be aware.
```javascript
constraints?: {
    network?: "connected" | "metered" | "notRoaming" | "unmetered" | "notRequired",
    battery?: "charging" | "notLow" | "notRequired",
    storage?: "notLow" | "notRequired",
    idle?: boolean,
}
```
constraints are used by the work manager to decide when to run a work, all defaults to "notRequired" or false.
```javascript
	repeatInterval?: number
```
Used only by the periodic worker, specifies the amount, in minutes, the work manager should wait until starting it again. The minimum value is 15 and it defaults to 15.

#### enqueue

```javascript
enqueue: (work) => Promise<string>
```
Enqueues a work a returns a promise that will resolve into the work's id,
the work parameter contains:
```javascript
worker:string
```
the name of the worker that will process this work.
```javascript
payload: any
```
the payload the worker will receive to do the job.

#### cancel
```javascript
cancel: (id: string) => Promise<void>
```
Cancels a periodic worker or a payload enqueued to some worker, given it's id

#### info
```javascript
info: (id: string) => Promise<{
    state: "failed" | "blocked" | "running" | "enqueued" | "cancelled" | "succeeded" | "unknown",
    attemptCount: number,
    value: any,
}>
```
This method returns a promise that will resolve in the info for work with the given id, the info contains the work's state, number of attempts and returned value.

#### addListener
```javascript
addListener: (id: string, listener: (info: WorkInfo) => void) => () => void
```
This method adds a listener for the given work that accepts the same parameters returned by info and returns the unsubscribe function

### HOOKS

#### useEnqueue
```javascript
useEnqueue: (work) => WorkInfo
```
This hook accepts the same parameters as the enqueue function, but returns the WorkInfo as a state hook to be updated automatically

#### useWorker
```javascript
useWorker: (worker, lifecycle?:"attached"|"detached") => WorkInfo|(payload: any) => WorkInfo 
```
this hook has two implementations, one for each worker type:

##### Periodic Worker
```javascript
useWorker: (worker, lifecycle?:"attached"|"detached") => WorkInfo
```
when used to register a periodic worker, the worker's lifecycle could be chosen, if the lifecycle is setted to attached, the worker will be cancelled when the component is unmounted, else the worker will remain working, defaults to attached. The hook will return a state hook containing the work's info

##### Queued Worker
```javascript
useWorker: (worker) => (payload: any) => WorkInfo
```
when used to register a queued worker, the hook will return a enqueue function that is linked with this worker and at each enqueue it returns the work's info as a state hook