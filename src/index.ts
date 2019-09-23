import { Dispatch, createStore, Store, AnyAction } from "redux"
import { Worker, Job, Archive, WorkFlow } from "./worker"
import { Persistor, persistStore, persistReducer } from "redux-persist"
import storage from "redux-persist-filesystem-storage"
import { PersistPartial } from "redux-persist/es/persistReducer"
import { createSelector } from "reselect"
import { useRef } from "react"



const createActions = (dispatch: Dispatch) => ({
    addWorker: (worker: { name: string, state: WorkerState }) => dispatch({ type: "ADD_WORKER" as const, worker }),
    deleteWorker: (workerName: any) => dispatch({ type: "DELETE_WORKER" as const, workerName }),
    pushJob: (workerName: string, job: Job<any>) => dispatch({ type: "PUSH_JOB" as const, workerName, job }),
    pushArchive: (workerName: string, toArchive: Archive<any,any>) => dispatch({ type: "PUSH_ARCHIVE" as const, workerName, toArchive }),
    purgeArchive: (workerName: string, keepTime: number) => dispatch({ type: "PURGE_ARCHIVE" as const, workerName, keepTime })
})

type ActionCreators = ReturnType<typeof createActions>
type Actions = ReturnType<ActionCreators[keyof ActionCreators]>
type ActionTypes = Actions["type"]

type Action<T extends keyof ActionCreators> = ReturnType<ActionCreators[T]>

interface WorkerState<P=any,V=any> {
    jobs: Job<P>[],
    archive: Archive<P,V>[]
}

interface State {
    [worker: string]: WorkerState
}

class WorkManager {

    static shared = new WorkManager()

    private store: Store<State & PersistPartial, AnyAction>
    private persistor: Persistor
    private actions: ActionCreators

    private workers: { [name: string]: Worker<any, any> } = {}

    private buffer: (() => void)[] = []

    private isStoreReady = false

    private constructor() {
        const [store, persistor, actions] = this.configureStore()
        this.store = store
        this.persistor = persistor
        this.actions = actions
    }

    private configureStore() {

        console.log(`configuring store`)

        function _reducer(state: State, action: Actions): State {

            console.log({ action })

            switch(action.type) {
                case "ADD_WORKER": return (({ worker: { name, state: workerState } }) => {
                    return ({ ...state, [name]: workerState })
                })(action)
                case "DELETE_WORKER": return (({ workerName }) => {
                    let { [workerName]: toDelete, ...newState } = state
                    return ({ ...newState })
                })(action)
                case "PUSH_JOB": return (({ workerName, job }) => {
                    let worker = state[workerName]
                    let jobs = [...worker.jobs, job].sort( (a,b) => {
                        if(a.priority>b.priority) return 1
                        if(a.priority<b.priority) return -1
                        return 0
                    })
                    return ({ ...state, [workerName]: { ...worker, jobs } })
                })(action)
                case "PUSH_ARCHIVE": return (({ workerName, toArchive }) => {
                    let worker = state[workerName]
                    let jobs = worker.jobs.filter(({ id }) => id != toArchive.job.id)
                    let archive = worker.archive
                    if(toArchive.job.attempts>1) {
                        jobs.push({ ...toArchive.job, attempts: toArchive.job.attempts - 1 })
                        jobs = jobs.sort((a,b) => a.priority>b.priority ? 1 : a.priority<b.priority ? -1 : 0)
                    }
                    archive.push(toArchive)
                    archive = archive.sort((a,b) => a.workDate>b.workDate ? 1 : a.workDate<b.workDate ? -1 : 0)
                    return ({ ...state, [workerName]: { jobs, archive } })
                })(action)
                case "PURGE_ARCHIVE": return (({ workerName, keepTime }) => {
                    console.log(`purging`)
                    let worker = state[workerName]
                    let archive = worker.archive.filter(({ workDate }) => new Date().getTime() - workDate < keepTime )
                    console.log(`new archive`, { archive })
                    return ({ ...state, [workerName]: { ...worker, archive } })
                })(action)
            }
            return state || {}
        }

        const reducer = (state: State | undefined, action: AnyAction) => _reducer(state || {}, action as Actions)

        const persistedReducer = persistReducer({ key: "react-native-background-worker", storage }, reducer)

        const _store = createStore(persistedReducer)
        const _actions = createActions(_store.dispatch)
        const _persistor = persistStore(_store, null, () => {
            console.log(`store is ready, starting workers`, { store: _store.getState() })
            this.isStoreReady = true
            this.buffer.map(func => func())
        })

        return [_store, _persistor, _actions] as [typeof _store, typeof _persistor, typeof _actions]
        
    }

    public addQueuedWorker<P,V=void>(name: string): Worker<P,V>  {

        // console.log(`creating worker ${ name }`)

        let workerState = {
            jobs: [] as Job<P>[],
            archive: [] as Archive<P,V>[],
        }
        
        const updateWorkerState = () => {
            if(this.isStoreReady) {
                let _workerState = this.store.getState()[name]
                if(!_workerState) this.actions.addWorker({ name, state: workerState })
                else workerState = {
                    jobs: [..._workerState.jobs],
                    archive: [..._workerState.archive],
                }
            }
        }

        this.store.subscribe(updateWorkerState)

        const worker = new Worker<P,V>({
            name,
            get jobs() { return workerState.jobs },
            get archive() { return workerState.archive },
            pushJob: (job) => this.actions.pushJob(name, job),
            pushArchive: (toArchive) => this.actions.pushArchive(name, toArchive),
            purgeArchive: (keepTime) => this.actions.purgeArchive(name, keepTime),
            start: (startScript) => {

                const startWorker = () => {
                    updateWorkerState()
                    startScript()
                }

                if(this.isStoreReady) startWorker()
                else this.buffer.push(startWorker)
            }
        })

        this.workers[name] = worker

        return worker
    }

    public worker<P,V>(name: string): Worker<P,V> | undefined {
        return this.workers[name]
    }

}

export const workManager = WorkManager.shared

export function useWorker<P,V>(name: string): Worker<P,V> {
    return workManager.worker(name) as Worker<P,V> || workManager.addQueuedWorker(name) as Worker<P,V>
}

useWorker("seila").withWorkflow<{ name: string }>(async ({ payload }) => {
    payload.name
}).enqueue({ name: "seila" })