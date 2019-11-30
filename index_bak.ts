import { NativeModules, AppRegistry, AppState, NativeAppEventEmitter } from "react-native"

export const setWorker = (worker: {
    type: "queued" | "periodic",
    name: string,
    constraints: {
        network: "connected" | "metered" | "notRoaming" | "unmetered" | "notRequired",
        battery: "charging" | "notLow" | "notRequired",
        storage: boolean,
        idle: boolean,
    },
    notification: {
        title: string,
        text: string,
    },
    workflow: (payload: any) => Promise<any>,
}) => {
    
    const { workflow, ..._worker } = worker

    const work = async (id: string, payload: string) => {
        try {
            const result = await workflow(JSON.parse(payload))
            NativeModules.BackgroundWorker.result(id, JSON.stringify(result), "success")
        }
        catch(error) {
            NativeModules.BackgroundWorker.result(id, JSON.stringify(error), "failure")
        }
    }

    AppRegistry.registerHeadlessTask(worker.name, () => async ({ payload, id }) => await work(id, payload))

    NativeAppEventEmitter.addListener(worker.name, ({ id, payload }) => {

        if(AppState.currentState==="active") work(id, payload)
        else NativeModules.BackgroundWorker.startHeadlessJS({ worker: worker.name, payload, id, ...worker.notification,  })

    })

    NativeModules.BackgroundWorker.setWorker(_worker)
}

export type WorkInfo = {
    state: "failed" | "blocked" | "running" | "enqueued" | "cancelled" | "succeeded" | "unknown",
    attempts: number,
    outputData: any,
}

export type WorkStatus = WorkInfo["state"]

export const enqueue = (work: {
    worker: string,
    payload: any,
    shouldRetry: boolean,
    listener ?: (workInfo: WorkInfo) => void,
}) => new Promise((resolve) => {
    NativeModules.BackgroundWorker
    .enqueue({
        worker: work.worker,
        payload: JSON.stringify(work.payload),
        shouldRetry: work.shouldRetry
    }, (id: string) => {
        const { listener } = work
        if(listener) {
            NativeModules.BackgroundWorker.registerListener(id)
            const subscription = NativeAppEventEmitter.addListener(id+"info", (_info) => listener({ ..._info, outputData: JSON.parse(_info.outputData) }))
            resolve({
                id,
                unsubscribe: () => {
                    NativeModules.BackgroundWorker.removeListener(id)
                    NativeAppEventEmitter.removeSubscription(subscription)
                }
            })
        }
        else resolve({ id })
    })
}) as Promise<{ id: string, unsubscribe ?: () => void }>

export const cancelWork = (id: string) => NativeModules.BackgroundWorker.cancelWorker(id)

export const workInfo = NativeModules.BackgroundWorker.workInfo as (id: string) => Promise<WorkInfo>

export const subscribe = (
    id: string,
    onChange: (workInfo: WorkInfo) => void,
) => {
    NativeModules.BackgroundWorker.registerListener(id)
    const subscription = NativeAppEventEmitter.addListener(id+"info", (_info) => onChange({ ..._info, outputData: JSON.parse(_info.outputData) }))
    return () => {
        NativeModules.BackgroundWorker.removeListener(id)
        NativeAppEventEmitter.removeSubscription(subscription)
    }
}

export const WorkManager = {
    setWorker,
    enqueue,
    cancelWork,
    workInfo,
    subscribe,
}