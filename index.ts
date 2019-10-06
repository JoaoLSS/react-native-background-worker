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

    AppRegistry.registerHeadlessTask(worker.name, () => async ({ payload, id }) => {
        console.log(`STARTING HEADLESS JS`)
        await work(id, payload)
    })

    NativeAppEventEmitter.addListener(worker.name, ({ id, payload }) => {

        if(AppState.currentState==="active") {
            console.log(`RUNNING IN FOREGROUND`)
            work(id, payload)
        }
        else {
            console.log(`CALLING HEADLESS JS`)
            NativeModules.BackgroundWorker.startHeadlessJS({ worker: worker.name, payload, id })
        }

    })

    NativeModules.BackgroundWorker.setWorker(_worker)

    // const register = () => AppRegistry.registerHeadlessTask(worker.name, () => async ({ payload, id }) => {

    //     try {
    //         const result = await workflow(JSON.parse(payload))
    //         NativeModules.BackgroundWorker.result(id, result)
    //     }
    //     catch(error) {
    //         NativeModules.BackgroundWorker.result(id, "failure")
    //     }

    // // })

    // register()
    // AppState.addEventListener("change", (state) => {
    //     console.log("BACKGROUND_RESSURGANCE", { state })
    //     state === "active" && register()
    // })
    // NativeAppEventEmitter.addListener("WAKE-UP", () => console.log("WAKE-UP"))
}

export const enqueue = (work: {
    worker: string,
    payload: any,
    shouldRetry: boolean,
}) => new Promise((resolve) => NativeModules.BackgroundWorker
    .enqueue({ worker: work.worker, payload: JSON.stringify(work.payload), shouldRetry: work.shouldRetry }, resolve)) as Promise<string>

export const cancelWork = (id: string) => NativeModules.BackgroundWorker.cancelWorker(id)

export const workInfo = (id: string) => new Promise((resolve) => NativeModules.BackgroundWorker.workInfo(id, resolve))

export const subscribe = (
    id: string,
    cb: (workInfo: {
        state: "failed" | "blocked" | "running" | "enqueued" | "cancelled" | "succeeded" | "unknown",
        attempts: number,
        outputData: any,
    }) => void,
) => {
    console.log(`registering listener for id ${id}`)
    NativeModules.BackgroundWorker.registerListener(id)
    NativeAppEventEmitter.addListener(id+"info", (_info) => cb({ ..._info, outputData: JSON.parse(_info.outputData) }))
    return () => NativeModules.BackgroundWorker.removeListener(id)
}