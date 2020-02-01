# react-native-background-worker

## Motivation

The react native community has some nice tools to work with background tasks, like [react-native-background-task](https://github.com/jamesisaac/react-native-background-task) and [react-native-background-fetch](https://github.com/transistorsoft/react-native-background-fetch), but those often offers some problems, as lack of maintenance, skipped tasks and so on. There is also [react-native-background-job](https://github.com/vikeri/react-native-background-job), but google is [deprecating](https://github.com/firebase/firebase-jobdispatcher-android) the firebase-job-dispatcher in favor of WorkManager's API. At the same time I liked so much the power and flexibility of WorkManager that I thought it would be awesome to bring those advantages into react native background tasks. So this is primarily a wrapper for the android work manager, with support for constrains, notification, persistence and much more, everything from the native side. For now this is heavily based on android's work manager and the react-native's headlessTask. Apple has realeased BGTaskScheduler recently and I'm planning to look on that, but I sincerely don't know if this module could have a simmetric implementation on the iOS side.

## Advantages

WorkManager offers a lot of advantages:
- Native support for constraints
- Native support for task persistence
- Native support for data persistence
- Smart schedule based on device state

If you want to know more see the WorkManager [documentation](https://developer.android.com/topic/libraries/architecture/workmanager)

## Changelog

- 0.0.5:
    - Bugfixes:
        - Workers were not been unregistered upon new registration, causing them to be called multiple times.
        - Unsubscription could crash the app

## To Do

- Next:
    - Add Backoff options
    - Add Notification actions
    - Add Notification progress
- Some day:
    - iOS implementation

## Getting started

`$ npm install react-native-background-worker --save`

### Mostly automatic installation (for RN<0.60)

`$ react-native link react-native-background-worker`

### Manual installation

#### Android

1. Open up `android/app/src/main/java/[...]/MainApplication.java`
  - Add `import com.backgroundworker;` to the imports at the top of the file
  - Add `new BackgroundWorkerPackage(reactContext)` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-background-worker'
  	project(':react-native-background-worker').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-background-worker/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      implementation project(':react-native-background-worker')
  	```
## Usage

### Simple Usage

```javascript

import WorkManager from 'react-native-background-worker';

workerId = await WorkManager.setWorker({
    type: 'periodic',
    name: 'someWorker',
    notification: {
        title: 'Notification Title',
        text: 'Notification Text',
    },
    workflow: async () => {

            // DO WORK

    },
});

```

## Examples

- [PeriodicWorker](https://github.com/JoaoLSS/react-native-background-worker/tree/master/examples/periodic)
- [QueueWorker](https://github.com/JoaoLSS/react-native-background-worker/tree/master/examples/queue)

## API

### setWorker

```typescript
WorkManager.setWorker({
    type: 'periodic'|'queue'
    name: string
    notification: {
        title: string
        text: string
    }
    workflow: (payload ?: any) => Promise<void|{ result: 'success'|'failure'|'retry', value: any }>
    timeout ?: number
    foregroundBehaviour ?: 'blocking'|'foreground'|'headlessTask'
    constraints ?: {
        network ?: 'connected'|'metered'|'notRoaming'|'unmetered'|'notRequired'
        battery ?: 'charging'|'notLow'|'notRequired'
        storage ?: 'notLow'|'notRequired'
        idle ?: 'idle'|'notRequired'
    }
    repeatInterval ?: number
}) => Promise<void|string>
```

- type [`'periodic'|'queue'`]:

    Worker type.

- name [`string`]:

    Worker name, remember to create a notification icon drawable with the same name to be used on the notification.

- notification:
    - title [`string`]:

        the title to be displayed on the notification.

    - text [`string`]:

        the text to be displayed on the notification.

- workflow:
    - periodic [`() => Promise<void>`]:

        the workflow to be perfomerd by the periodic worker, it doesn't receive anything and should return nothing.

    - queue [`(payload: any) => Promise<{ result: 'success'|'failure'|'retry', value: any }>`]:

        the worflow to be performed by the queue worker, it will receive the enqueued payload and should return an object containing the result, which could be
        'success','failure' or 'retry' (in that case the worker will be reescheduled with the same payload), and optionaly a result value to be stored.

- timeout [`number`][optional]:

    the timeout in minutes for the HeadlessTask, the maximum value is 10, it defaults to 10.

- foregroundBehaviour [`'blocking'|'foreground'|'headlessTask'`][optional]:

    !!CAUTION!!

    This variable sets the worker's behaviour when the app is in foreground. If this is set to headlessTask, the worker will start the headless service to execute the task, this could be necessary to long performing tasks that need to transition between app states, since in background async tasks tend to be a little unpredictable. It will also show the notification, since it is obliged to, this could be the reason why someone would choose the foreground mode, where the task is started as a normal async call, this will not show any notification, but could have an unpredictable behaviour if the app goes to background in the middle of the task. At last, the blocking behaviour is the default behaviour and, [quoting](https://facebook.github.io/react-native/docs/headless-js-android#caveats) the react native documentation, "This is to prevent developers from shooting themselves in the foot by doing a lot of work in a task and slowing the UI.", Since react is single threaded, the two other behaviours could make your UI sluggish, so be aware.

- constraints [optional]:

    WorkManager's constraints, to know more see the [documentation](https://developer.android.com/reference/androidx/work/Constraints.html).

    - network [`'connected'|'metered'|'notRoaming'|'unmetered'|'notRequired'`][optional]:

        worker constraint concerning network. Defaults to 'notRequired'.

    - battery [`'charging'|'notLow'|'notRequired'`][optional]:

        worker constraint concerning battery. Defaults to 'notRequired'.

    - storage [`'notLow'|'notRequired'`][optional]:

        worker constraint concerning storage. Defaults to 'notRequired'.

    - idle [`'idle'|'notRequired'`][optional]:

        worker constraint concerning the device state. Defaults to 'notRequired'.

- repeatInterval [`number`][optional][only for periodic worker]:

    the time workmanager should wait to call the worker again in minutes. The minimum value is 15, defaults to 15.

- returns:

    the setWorker method returns a promise that will resolve with the worker's id in case of periodic or void in case of queue, or it will reject if the
    worker could not be registered.

### enqueue

```typescript
WorkManager.enqueue({
    worker: string
    payload ?: any
}) => Promise<string>
```

this method is used only for queue workers

- worker [`string`]:

    the name of the worker that will work upon this payload, remember to register said worker before calling enqueue.

- payload [`any`][optional]:

    the payload to be processed by the worker. This is optional because you can create a queue worker that receives nothing. THE PAYLOAD HAS TO MATCH
    THE TYPE WORKER IS EXPECTING, otherwise your worker will fail.

- returns:

    this method returns a promise that will resolve into the work's id for this payload, or it will reject if the payload could not be enqueued.

### cancel

```typescript
WorkManager.cancel(id: string) => Promise<void>
```

this method is used to cancel a worker. Note that if the worker is already running it will not stop.

- id [`string`]:

    the id returned by setWorker or enqueue.

- returns:

    this method returns a promise that resolves if the worker has been cancelled or rejects otherwise.

### info

```typescript
    WorkManager.info(id: string) => Promise<{
        state: 'failed'|'blocked'|'running'|'enqueued'|'cancelled'|'succeeded'|'unknown'
        attemptCount: number
        value: any
    }>
```

this method is used to fetch the workers info

- id [`string`]:

    the id returned by setWorker or enqueue.

- returns:

    this returns a promise that will reject if the worker info is not found or resolve with the following result:

    - state [`'failed'|'blocked'|'running'|'enqueued'|'cancelled'|'succeeded'|'unknown'`]:

    Worker's state, if the worker is queue it will assume any of these states, if it is periodic it will never be 'failed' or 'succeeded'.

    - attemptCount [`number`]:

    This will appear only if the worker is queue. It shows how many times the worker attempted to process the payload attached to this id.

    - value [`any`]:

    This is also used only with the queue worker, it shows what was the returning value for this payload if it was already processed.

### addListener

```typescript
    WorkManager.addListener(
        id: string,
        callback: ({
            state: 'failed'|'blocked'|'running'|'enqueued'|'cancelled'|'succeeded'|'unknown'
            attemptCount: number
            value: any
        }) => void
    ) => () => void
```

this adds a listener to changes on worker's state.

- id [`string`]:

    the id returned by setWorker or enqueue.

- callback[`({ state, attemptCount, value }) => void`]:

    the callback which will receive the same information returned by the info method, once there's a change on worker's state.

- returns:

    this returns a method to unsubscribe the listener.