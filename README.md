# react-native-background-worker

## Motivation

The react native community has some nice tools to work with background tasks, like [react-native-background-task](https://github.com/jamesisaac/react-native-background-task) and [react-native-background-fetch](https://github.com/transistorsoft/react-native-background-fetch), but those often offers some problems, as lack of maintenance, skipped tasks and so on. At the same time I liked so much the power and flexibility of WorkManager that I thought it would be awesome to bring those advantages into react native background tasks. So this is primarily a wrapper for the android work manager, with support for constrains, notification, persistence and much more, everything from the native side. For now this is heavily based on android's work manager and the react-native's headlessTask. Apple has realeased BGTaskScheduler recently and I'm planning to look on that, but I sincerely don't know if this module could have a simmetric implementation on the iOS side.

## To Do

- Next:
    - Add Backoff options
    - Add Notification actions
- Some day:
    - iOS implementation

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
# Usage

## Periodic Worker

Let's say you are working on a news feed app, and want to update the news every 15 minutes when the app is in foreground and every 30 minutes in background, you also want to skip the task if the device is offline, here's how you can implement this using background-worker:

```javascript

import { AppState } from 'react-native';
import WorkManager from 'react-native-background-worker';

export let updaterId;

// this is how to set a periodic worker
async function setUpdater(repeatInterval) {
    updaterId = await WorkManager.setWorker(/*periodic worker:*/{
        type: 'periodic',                                   // [REQUIRED] worker's type, could be 'periodic' or 'queue'.
        name: 'newsUpdater',                                // [REQUIRED] worker's name, remember to create a drawable with the
                                                            // same name to be displayed with the notification.
        notification: {
            title: 'Updating your news',                    // [REQUIRED] notification title.
            text: 'Don`t worry, we will keep you fresh 😎', // [REQUIRED] notification text.
        },
        workflow: async () => {                             // [REQUIRED] the workflow this worker will perform.

            const freshNews = await myNewsFactoryApi.getNewsForUser(userId)
            someDataManager.save({ freshNews })

        },
        timeout: 1,                                         // [OPTIONAL] the headless task timeout in minutes, defaults to 10.
        foregroundBehaviour: 'foreground'                   // [OPTIONAL] the worker's behaviour when app is in foreground,
                                                            // could be 'blocking','foreground' or 'headlessTask', since react is
                                                            // very sensible to tasks that demand processing power, this default
                                                            // to blocking.
        constraints: {
            network: 'connected',                           // [OPTIONAL] network constraint for this worker.
            battery: 'notRequired',                         // [OPTIONAL] battery constraint for this worker.
            storage: 'notRequired',                         // [OPTIONAL] storage constraint for this worker.
            idle: 'notRequired',                            // [OPTIONAL] usage constraint for this worker.
        },
        repeatInterval,                                     // [OPTIONAL] used only with periodic workers, sets the time in minutes
                                                            // the work manager will wait until launching this task again, minimum
                                                            // is 15, defaults to 15.
    });
}

//if you register two workers with the same name, one will replace the other,
//so you can keep registering the same worker to change it's configuration
AppState.addEventListener('change',(state) => {
    switch(state) {
        case 'active':
            setUpdater(15);
            break;
        case 'background':
            setUpdater(30);
            break;
    }
});

//once you setted the worker, you can manipulate it through the returned id
export async function finalizeUpdater() {
     await WorkManager.cancel(updaterId);
}

export async function updaterInfo() {
    return await WorkManager.info(updaterId);
}

//And moreover in some component you can listen to this worker to know when it is executing
const unsubscriber = WorkManager.addListener(updaterId,(info) => {
    if(info.state==='running') {
        //show the user some spinning thing
    }
});

//and unsubscribe later
unsubscriber();

```

the periodic worker object should assume this contract:

```typescript

interface PeriodicWorker {
    type: 'periodic'
    name: string
    notification: {
        title: string
        text: string
    },
    workflow: () => Promise<void>
    timeout?: number
    foregroundBehaviour?: 'headlessTask'|'foreground'|'blocking'
    constraints?: {
        network?: 'connected'|'metered'|'notRoaming'|'unmetered'|'notRequired'
        battery?: 'charging'|'notLow'|'notRequired'
        storage?: 'notLow'|'notRequired'
        idle?: 'idle'|'notRequired'
    },
    repeatInterval?: number,
}

```

## Queue Worker

There's two main utilizations for this worker, obviously as a queue and as a one time worker.

### One Time Worker

One day you see yourself working on a music app, and one of the views you are working on is the playlist view, with one button allowing the user to download the entire playlist, but you only wanna do that if the device is connected to internet, has enough power and has space to that, and you want to continue the work aside of app state changes, how would you implement it? You could easily achieve this with background-worker:

```javascript

import React from 'react';
import WorkManager from 'react-native-background-worker';

class PlaylistView extends React.Component {

    state = { downloadId: undefined }

    componentDidMount() {
        WorkManager.setWorker(/*queue worker:*/{
            type: 'queue',                                      // [REQUIRED] worker's type, could be 'periodic' or 'queue'.
            name: 'playlistDownloader',                         // [REQUIRED] worker's name, remember to create a drawable with the
                                                                // same name to be displayed with the notification.
            notification: {
                title: 'Downloading your playlist',             // [REQUIRED] notification title.
                text: 'You can start dancing already 🎶',       // [REQUIRED] notification text.
            },
            workflow: async () => {                             // [REQUIRED] the workflow this worker will perform.

                try {
                    const offlinePlaylist = await notSpotifyApi.downloadPlaylist(playlistId)
                    someDataManager.save({ offlinePlaylist })
                    return { result: 'success', value: { size: offlinePlaylist.size } }
                }
                catch(error) {
                    return { result: 'retry', value: { error } }
                }

            },
            timeout: 30,                                        // [OPTIONAL] the headless task timeout in minutes, defaults to 10.
            foregroundBehaviour: 'headlessTask'                 // [OPTIONAL] the worker's behaviour when app is in foreground,
                                                                // could be 'blocking','foreground' or 'headlessTask', since react is
                                                                // very sensible to tasks that demand processing power, this default
                                                                // to blocking.
            constraints: {
                network: 'unmetered',                           // [OPTIONAL] network constraint for this worker.
                battery: 'notLow',                              // [OPTIONAL] battery constraint for this worker.
                storage: 'notLow',                              // [OPTIONAL] storage constraint for this worker.
                idle: 'notRequired',                            // [OPTIONAL] usage constraint for this worker.
            }
        })
        .then(() => console.log(`downloader setted`))
        .catch(() => console.log(`error setting downloader`))
    }

    onDowloadButtonPress() {
        WorkManager.enqueue({ worker: 'playlistDownloader' })   // After the enqueue function is called, the work is registered with
            .then((downloaderId) => setState({ downloaderId })) // work manager and it constraints are met, should start right away
                                                                // not always though, since work is scheduled by the work manager
    }

    async downloadInfo() {
        if(state.downloaderId) {
            return await WorkManager.info(state.downloaderId)
        }
        else return Promise.reject()
    }

}

```

### Queue Worker

Now you are working in a photo backup service, and everytime a new photo is taken it should start uploading, here's how to implement using background-worker:

```javascript

import React from 'react';
import WorkManager from 'react-native-background-worker';

class AlbumView extends React.Component {

    uploadWorks = {}

    componentDidMount() {
        WorkManager.setWorker(/*queue worker:*/{
            type: 'queue',                                          // [REQUIRED] worker's type, could be 'periodic' or 'queue'.
            name: 'photoUploader',                                  // [REQUIRED] worker's name, remember to create a drawable with the
                                                                    // same name to be displayed with the notification.
            notification: {
                title: 'Uploading your photos',                     // [REQUIRED] notification title.
                text: 'We like to keep your memories with care 😁', // [REQUIRED] notification text.
            },
            workflow: async (payload: { uri }) => {                 // [REQUIRED] the workflow this worker will perform.

                try {
                    const data = await notGooglePhotosAPI.uploadPhoto(uri)
                    someDataManager.save({ data })
                    return { result: 'success', value: { url: data.url } }
                }
                catch(error) {
                    return { result: 'retry', value: { error } }
                }

            },
            timeout: 1,                                         // [OPTIONAL] the headless task timeout in minutes, defaults to 10.
            foregroundBehaviour: 'headlessTask'                 // [OPTIONAL] the worker's behaviour when app is in foreground,
                                                                // could be 'blocking','foreground' or 'headlessTask', since react is
                                                                // very sensible to tasks that demand processing power, this default
                                                                // to blocking.
            constraints: {
                network: 'unmetered',                           // [OPTIONAL] network constraint for this worker.
                battery: 'notRequired',                         // [OPTIONAL] battery constraint for this worker.
                storage: 'notRequired',                         // [OPTIONAL] storage constraint for this worker.
                idle: 'notRequired',                            // [OPTIONAL] usage constraint for this worker.
            }
        })
        .then(() => console.log(`uploader setted`))
        .catch(() => console.log(`error setting uploader`))
    }

    uploadPhoto(uri) {
        WorkManager.enqueue({
            worker: 'photoUploader',
            payload: { uri }
        }).then((uploadId) => uploadWorks.push(uploadeId))
    }

}

```

the queue worker object should assume a slightly different contract:

```typescript

interface QueueWorker {
    type: 'queue'
    name: string
    notification: {
        title: string
        text: string
    },
    workflow: (payload: any) => Promise<{ result:'success'|'failure'|'retry', value: any }>
    timeout?: number
    foregroundBehaviour?: 'headlessTask'|'foreground'|'blocking'
    constraints?: {
        network?: 'connected'|'metered'|'notRoaming'|'unmetered'|'notRequired'
        battery?: 'charging'|'notLow'|'notRequired'
        storage?: 'notLow'|'notRequired'
        idle?: 'idle'|'notRequired'
    },
}

```

### foregroundBehaviour

This variable needs caution, it sets the behaviour of this worker when the app is in foreground. If this is set to headlessTask, the worker will start the headless service to execute the task, this could be necessary to long performing tasks that need to transition between app states, since in background async tasks tend to be a little unpredictable. It will also show the notification, since it is obliged to, this could be the reason why someone would choose the foreground mode, where the task is started as a normal async call, this will not show any notification, but could have an unpredictable behaviour if the app goes to background in the middle of the task. At last, the blocking behaviour is the default behaviour and, quoting the react native documentation, "This is to prevent developers from shooting themselves in the foot by doing a lot of work in a task and slowing the UI.", Since react is single threaded, the two other behaviours could make your UI sluggish, so be aware.