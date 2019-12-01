import { NativeModules, AppRegistry, AppState, NativeAppEventEmitter } from "react-native"

interface GenericWorker<T extends "periodic"|"queued"> {
    type: T
    name: string,
    timeout?: number,
    foregroundBehaviour?: "headlessTask" | "foreground" | "blocking",
    constraints?: {
        network?: "connected" | "metered" | "notRoaming" | "unmetered" | "notRequired",
        battery?: "charging" | "notLow" | "notRequired",
        storage?: "notLow" | "notRequired",
        idle?: boolean,
    },
    notification: {
        title: string,
        text: string,
    },
}

interface QueuedWorker<P,V,T extends "queued"> extends GenericWorker<T> {
    workflow: (payload: P) => Promise<{ result: "success" | "failure" | "retry", value: V }>
    repeatInterval?: never,
}

const isQueuedWorker = (worker: any): worker is QueuedWorker<any,any,"queued"> => worker.type && worker.type==="queued"

interface PeriodicWorker<T extends "periodic"> extends GenericWorker<T> {
    workflow: () => Promise<void>,
    repeatInterval?: number,
}

const isPeriodicWorker = (worker: any): worker is PeriodicWorker<"periodic"> => worker.type && worker.type==="periodic"

type Worker<P,V,T extends "queued"|"periodic"> = T extends "queued" ? QueuedWorker<P,V,T> : T extends "periodic" ? PeriodicWorker<T> : never

function setWorker<T extends "queued"|"periodic",P=any,V=any>(worker: Worker<P,V,T>): Promise<T extends "periodic" ? string:void> {

    const { workflow, constraints, notification, ..._worker } = worker
    const workerConfiguration = { repeatInterval: 15, timeout: 10, foregroundBehaviour: "blocking", ..._worker, ...notification }

    const work = async (data: { id: string, payload: string }) => {
        try {
            if(isPeriodicWorker(worker)) {
                await worker.workflow()
                NativeModules.BackgroundWorker.result(data.id, JSON.stringify(null), "success")
            }
            else if(isQueuedWorker(worker)) {
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
        NativeModules.BackgroundWorker.startHeadlessTask({ ...workerConfiguration, ...data })
        
    })

    return NativeModules.BackgroundWorker.registerWorker(workerConfiguration,constraints||{})

}

function enqueue(work: { worker: string, payload: any }): Promise<string> {
    return NativeModules.BackgroundWorker.enqueue(work.worker, JSON.stringify(work.payload))
}

export default {
    setWorker,
    enqueue
}