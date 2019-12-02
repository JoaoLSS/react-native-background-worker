import { NativeModules, AppRegistry, AppState, NativeAppEventEmitter } from "react-native"

interface GenericWorker<T extends "periodic"|"queue"> {
    type: T
    name: string,
    timeout?: number,
    foregroundBehaviour?: "headlessTask" | "foreground" | "blocking",
    constraints?: {
        network?: "connected" | "metered" | "notRoaming" | "unmetered" | "notRequired",
        battery?: "charging" | "notLow" | "notRequired",
        storage?: "notLow" | "notRequired",
        idle?: "idle"|"notRequired",
    },
    notification: {
        title: string,
        text: string,
    },
}

interface QueueWorker<P,V,T extends "queue"> extends GenericWorker<T> {
    workflow: (payload: P) => Promise<{ result: "success" | "failure" | "retry", value: V }>
    repeatInterval?: never,
}

export const isQueueWorker = (worker: any): worker is QueueWorker<any,any,"queue"> => worker.type && worker.type==="queue"

interface PeriodicWorker<T extends "periodic"> extends GenericWorker<T> {
    workflow: () => Promise<void>,
    repeatInterval?: number,
}

export const isPeriodicWorker = (worker: any): worker is PeriodicWorker<"periodic"> => worker.type && worker.type==="periodic"

export type Worker<P,V,T extends "queue"|"periodic"> = T extends "queue" ? QueueWorker<P,V,T> : T extends "periodic" ? PeriodicWorker<T> : never

/**
 * Function used to schedule workers
 * If the worker is periodic, it will be registered right away and should start ASAP,
 * if the worker is queued, it`s information is stored on the native side because each enqueue is registered as a one time work request
 * @param worker the worker information to be scheduled
 */
function setWorker<T extends "queue"|"periodic",P=any,V=any>(worker: Worker<P,V,T>): Promise<T extends "periodic" ? string:void> {

    const { workflow, constraints, notification, ..._worker } = worker
    const workerConfiguration = { repeatInterval: 15, timeout: 10, foregroundBehaviour: "blocking", ..._worker, ...notification }

    const work = async (data: { id: string, payload: string }) => {
        try {
            // if worker is periodic, it has no return value
            if(isPeriodicWorker(worker)) {
                await worker.workflow()
                NativeModules.BackgroundWorker.result(data.id, JSON.stringify(null), "success")
            }
            // if worker is queue, capture it`s return value to save it
            else if(isQueueWorker(worker)) {
                const { result, value } = await worker.workflow(JSON.parse(data.payload))
                NativeModules.BackgroundWorker.result(data.id, JSON.stringify(value), result)
            }
            else { throw "INCOMPATIBLE_TYPE" }
        }
        catch(error) {
            NativeModules.BackgroundWorker.result(data.id, JSON.stringify(error), "failure")
        }
    }

    AppRegistry.registerHeadlessTask(worker.name, () => work)
    NativeAppEventEmitter.addListener(worker.name, (data) => {

        // if the app is in foreground we should see for the foreground behaviour
        // if the behaviour is blocking, task is blocked and is scheduled to retry
        // if the behaviour is foreground, the task starts in normal mode
        if(AppState.currentState==="active") {
            if(workerConfiguration.foregroundBehaviour==="blocking") {
                NativeModules.BackgroundWorker.result(data.id, JSON.stringify(null), "retry")
                return
            }
            if(workerConfiguration.foregroundBehaviour==="foreground") {
                work(data)
                return
            }
        }
        //if none of alternatives was achieved we should start the headlessTask
        NativeModules.BackgroundWorker.startHeadlessTask({ ...workerConfiguration, ...data })
        
    })

    return NativeModules.BackgroundWorker.registerWorker(workerConfiguration,constraints||{})

}

/**
 * This function enqueue a payload to be processed by a registered queue worker
 * @param work The worker name and payload to be scheduled
 */
function enqueue(work: { worker: string, payload?: any }): Promise<string> {
    return NativeModules.BackgroundWorker.enqueue(work.worker, JSON.stringify(work.payload))
}

/**
 * Cancels a registered work
 * @param id work's id to be canceled
 */
function cancel(id: string): Promise<void> {
    return NativeModules.BackgroundWorker.cancel(id)
}

export type WorkInfo<V> = {
    state: "failed" | "blocked" | "running" | "enqueued" | "cancelled" | "succeeded" | "unknown",
    attemptCount: number,
    value: V,
}

/**
 * Returns the WorkInfo object for the requested work
 * @param id requisited work's id
 */
function info<V>(id: string): Promise<WorkInfo<V>> {
    return new Promise((resolve,reject) => {
        NativeModules.BackgroundWorker.info(id)
            .then((_info: WorkInfo<string>) => resolve({ ..._info, value: JSON.parse(_info.value) }))
            .catch(reject)
    })
}

/**
 * Registers a listener to watch for changes on work's state
 * @param id requisited work's id
 * @param callback function to be called when work's state change
 */
function addListener<V>(id: string,callback: (info: WorkInfo<V>) => void): () => void {
    NativeModules.BackgroundWorker.addListener(id)
    const subscription = NativeAppEventEmitter.addListener(id+"info", (_info: WorkInfo<string>) =>
        callback({ ..._info, value: JSON.parse(_info.value) }))
    return () => {
        subscription.remove()
        NativeModules.BackgroundWorker.removeListener(id)
    }
}

export default {
    setWorker,
    enqueue,
    cancel,
    info,
    addListener,
}