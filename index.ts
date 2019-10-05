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
    workflow: (payload: any) => Promise<"success" | "failure" | "retry">,
}) => {
    
    const { workflow, ..._worker } = worker

    NativeModules.BackgroundWorker.setWorker(_worker)
    const register = () => AppRegistry.registerHeadlessTask(worker.name, () => async ({ payload, id }) => {

        try {
            const result = await workflow(JSON.parse(payload))
            NativeModules.BackgroundWorker.result(id, result)
        }
        catch(error) {
            NativeModules.BackgroundWorker.result(id, "failure")
        }

    })

    register()
    AppState.addEventListener("change", (state) => {
        console.log("BACKGROUND_RESSURGANCE", { state })
        state === "active" && register()
    })
    NativeAppEventEmitter.addListener("WAKE-UP", () => console.log("WAKE-UP"))
}

export const enqueue = (work: {
    worker: string,
    payload: any,
}) => new Promise((resolve) => NativeModules.BackgroundWorker
    .enqueue({ worker: work.worker, payload: JSON.stringify(work.payload) }, resolve)) as Promise<string>

export const cancelWork = (id: string) => NativeModules.BackgroundWorker.cancelWorker(id)

export const workInfo = (id: string) => new Promise((resolve) => NativeModules.BackgroundWorker.workInfo(id, resolve))