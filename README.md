# react-native-background-worker

## Motivation

The react native community has some nice tools to work with background tasks, like [react-native-background-task](https://github.com/jamesisaac/react-native-background-task) and [react-native-background-fetch](https://github.com/transistorsoft/react-native-background-fetch), but those often offers some problems, as lack of maintenance, skipped tasks and so on. There is also [react-native-background-job](https://github.com/vikeri/react-native-background-job), but google is [deprecating](https://github.com/firebase/firebase-jobdispatcher-android) the firebase-job-dispatcher in favor of WorkManager's API. At the same time I liked so much the power and flexibility of WorkManager that I thought it would be awesome to bring those advantages into react native background tasks. So this is primarily a wrapper for the android work manager, with support for constrains, notification, persistence and much more, everything from the native side. For now this is heavily based on android's work manager and the react-native's headlessTask. Apple has realeased BGTaskScheduler recently and I'm planning to look on that, but I sincerely don't know if this module could have a simmetric implementation on the iOS side.

## Advantages

WorkManager offers a lot of advantages:
- Native support for contrains
- Native support for task persistence
- Native support for data persistence
- Smart schedule based on device state

If you want to know more see the WorkManager [documentation](https://developer.android.com/topic/libraries/architecture/workmanager)

## Changelog

- 0.0.5:
    - Bugfixes:
        - Works were not been unregistered upon new registration, causing them to be called multiple times.
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
# Usage

## Simple Usage

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

- [PeriodicWorker](https://github.com/JoaoLSS/react-native-background-worker/tree/PeriodicExample)

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
})
```

- type [`'periodic'|'queue'`]:
    Worker type

- name [`string`]:
    Worker name, remember to create a notification icon drawable with the same name to be used on the notification.

- notification:
    - title [`string`]:

        the title to be displayed on the notification

    - text [`string`]:

        the text to be displayed on the notification


- workflow:
    - periodic [`() => Promise<void>`]:

### foregroundBehaviour

This variable needs caution, it sets the behaviour of this worker when the app is in foreground. If this is set to headlessTask, the worker will start the headless service to execute the task, this could be necessary to long performing tasks that need to transition between app states, since in background async tasks tend to be a little unpredictable. It will also show the notification, since it is obliged to, this could be the reason why someone would choose the foreground mode, where the task is started as a normal async call, this will not show any notification, but could have an unpredictable behaviour if the app goes to background in the middle of the task. At last, the blocking behaviour is the default behaviour and, quoting the react native documentation, "This is to prevent developers from shooting themselves in the foot by doing a lot of work in a task and slowing the UI.", Since react is single threaded, the two other behaviours could make your UI sluggish, so be aware.