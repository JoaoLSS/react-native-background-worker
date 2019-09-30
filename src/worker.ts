import { NativeModules, AppRegistry, AppState, NativeEventEmitter, NativeAppEventEmitter } from "react-native"
import now from "performance-now"
import cuid from "cuid"

export interface Job<P> {
    id: string
    payload: P
    priority: number
    attempts: number
    enqueuedDate: number
}

interface PreArchive<P> {
    job: Job<P>
    workflowDuration: number
    jobDuration: number
    workDate: number
}

interface SuccessArchive<P,V> extends PreArchive<P> {
    status: "success"
    value: V
}

interface FailureArchive<P> extends PreArchive<P> {
    status: "failure"
    error: any
}

export type Archive<P,V> = FailureArchive<P> | SuccessArchive<P,V>
export type JobResult = Archive<any,any>["status"]

export interface Options<P,V> {
    notification: {
        showProgress: boolean
        cancelable: false | string
        title: string
        message: string
        actions: {
            [action: string]: () => void
        }
    }
    concurrency: number,
    keepTime: number,
    autoStart: boolean
    callbacks: {
        onStart?: (job: Job<P>) => Promise<void>
        onSuccess?: (job: Job<P>, value: V) => Promise<void>
        onFailure?: (job: Job<P>, error: any) => Promise<void>
        onFailed?: (job: Job<P>, error: any) => Promise<void>
        onComplete?: (job: Job<P>) => Promise<void> 
    }
}

export interface Constraints {
    network: "connected" | "metered" | "notRoaming" | "unmetered" | "notRequired",
    battery: "charging" | "notLow" | "notRequired",
    storage: boolean,
    idle: boolean,
}

export type WorkFlow<P,V> = (job: Job<P>) => Promise<V>

export interface ManagerStub<P,V> {
    readonly name: string
    readonly jobs: Job<P>[]
    readonly archive: Archive<P,V>[]
    pushJob: (job: Job<P>) => void
    pushArchive: (toArchive: Archive<P,V>) => void
    purgeArchive: (keepTime: number) => void
    start: (subscriber: () => void) => void
}

export class Worker<P,V=never> {

    private id ?: string

    private emitter = new NativeEventEmitter()

    private eventType: string | undefined = undefined

    private cancelTask ?: () => void

    private _status: "intializing" | "idle" | "listening" | "running" = "intializing"

    public get status() {
        return String(this._status) as typeof Worker.prototype._status
    }

    public get queue() {
        const _jobs = this.store.jobs
        return _jobs.map( ({ enqueuedDate, ...job }) => ({
            ...job,
            enqueuedDate: new Date(enqueuedDate),
        }) )
    }

    public get archive() {
        const _archive = this.store.archive
        return _archive.map( ({ workDate, workflowDuration, job, jobDuration, ...archive }) => ({
            ...archive,
            job: {
                ...job,
                enqueuedDate: new Date(job.enqueuedDate)
            },
            workDate: new Date(workDate),
            workflowDuration: `${ Math.ceil(workflowDuration/1000) } segundos`,
            jobDuration: `${ Math.ceil(jobDuration/1000) } segundos`
        }) )
    }

    private buffer: (() => void)[] = []

    private options: Options<P,V> = {
        notification: {
            showProgress: false,
            cancelable: false,
            title: this.store.name,
            message: "set the notification options for this worker to change this notification.",
            actions: {
                ok: () => console.log(`user pressed ok`)
            }
        },
        concurrency: 1,
        keepTime: 365*24*60*60*1000,
        autoStart: true,
        callbacks: {}
    }

    private constraints: Constraints = {
        network: "notRequired",
        battery: "notRequired",
        storage: false,
        idle: false,
    }

    private _workflow: WorkFlow<P,V> = () => { throw "WORKFLOW_NOT_SET" }
    private get workflow() {
        return async () => {

            if(this._status==="running") return

            this._status = "running"

            const beforeJobs = Array.from(this.store.jobs)

            if(this.options.notification.showProgress) NativeModules.BackgroundWorker.setProgress(this.id, beforeJobs.length, 0)

            let cancelled = false

            do {

                if(__DEV__)
                console.log(`running worker ${ this.store.name }`, { jobs: this.store.jobs, concurrency: this.options.concurrency })

                const { onStart, onSuccess, onFailure, onFailed, onComplete } = this.options.callbacks

                const jobs = Array.from(this.store.jobs)

                const workerInstances = [...Array(this.options.concurrency).keys()].map(async (instance: number) => {

                    const log = (arg: string) => __DEV__ && console.log(
                        new Date(now()).toISOString().split(`T`)[1]+
                        `${ this.store.name }::${ instance }::`+arg)

                    log(`looking for a job`)

                    let job = jobs.pop()
                    if(!job) return

                    log(`initializing ${ job.id }`)

                    const startWorkflowTime = now()
                    const workflowDuration = () => now() - startWorkflowTime

                    let startJobTime: number | undefined
                    let jobDuration: number = 0
                    let value: V | undefined
                    let error: any

                    let result: JobResult = "failure"

                    try {
                        
                        if(onStart) {
                            log(`onStart callback found, running it`)
                            try { await onStart(job) }
                            catch(error) { log(`onStart callback throw ${ error }`) }
                        }

                        log(`running main workflow`)

                        startJobTime = now()
                        value = await this._workflow(job)
                        jobDuration = now() - startJobTime
                        result = "success"

                        log(`worflow was successful`)

                        if(onSuccess) {
                            log(`onSuccess callback found, running it`)
                            try { await onSuccess(job, value) }
                            catch(error) { log(`onSuccess callback throw ${ error }`) }
                        }

                        log(`archiving successful result`)
                    }
                    catch(error) {

                        jobDuration = startJobTime ? now() - startJobTime : 0

                        log(`worflow failed`)

                        if(onFailure) {
                            log(`onFailure callback found, running it`)
                            try { await onFailure(job, error) }
                            catch(error) { log(`onFailure callback throw ${ error }`) }
                        }

                        if(job.attempts==1 && onFailed) {
                            log(`onFailed callback found, running it`)
                            try { await onFailed(job, error) }
                            catch(error) { log(`onFailed callback throw ${ error }`) }
                        }

                        log(`archiving failed result`)
                    }
                    finally {

                        if(onComplete) {
                            log(`onComplete callback found, running it`)
                            try { await onComplete(job) }
                            catch(error) { log(`onComplete callback throw ${ error }`) }
                        }

                        const toArchive = {
                            job,
                            workflowDuration: workflowDuration(),
                            jobDuration,
                            workDate: new Date().getTime(),
                        }

                        this.store.pushArchive(
                            result === "success" && value ?
                                { ...toArchive, status: "success", value }
                                :
                                { ...toArchive, status:"failure", error }
                        )

                        if(this.options.notification.showProgress)
                            NativeModules.BackgroundWorker.setProgress(this.id, beforeJobs.length, beforeJobs.length - this.store.jobs.length)

                    }

                })
                
                try {
                    if(this.options.notification.cancelable) {
                        const cancelPromise = new Promise<void>((resolve, reject) => this.cancelTask = () => {
                            cancelled = true
                            reject()
                        })
                        await Promise.race([Promise.all(workerInstances),cancelPromise])
                    }
                    else await Promise.all(workerInstances)
                    NativeModules.BackgroundWorker.success(this.id)
                } catch(error) {
                    NativeModules.BackgroundWorker.failure(this.id)
                }

            } while(AppState.currentState==="active" && this.store.jobs.length && !cancelled)

            // console.log(`finish running worker ${ this.store.name }`)

            this._status = "listening"

        }
    }

    constructor(private store: ManagerStub<P,V>) {
        store.start(() => {
            console.log(`starting worker ${ this.store.name }, with jobs`, this.store.jobs)
            this._status = "idle"
            this.buffer.map(func => func())
            this.store.purgeArchive(this.options.keepTime)
            this.start()
        })
    }

    public withOptions(options: {
        notification ?: {
            showProgress: boolean
            cancelable: false | string
            title: string
            message: string
            actions: {
                [action: string]: () => void
            }
        }
        concurrency ?: number,
        keepTime ?: number | "aMinute" | "aHour" | "aDay" | "aWeek" | "aMonth" | "aYear",
        autoStart ?: boolean
        callbacks ?: {
            onStart?: (job: Job<P>) => Promise<void>
            onSuccess?: (job: Job<P>, value: V) => Promise<void>
            onFailure?: (job: Job<P>, error: any) => Promise<void>
            onFailed?: (job: Job<P>, error: any) => Promise<void>
            onComplete?: (job: Job<P>) => Promise<void> 
        }
    }) {
        if(this._status==="intializing") this.buffer.push(() => this.withOptions(options))
        else {
            if(options.notification && options.notification.cancelable) {
                options.notification.actions[options.notification.cancelable] = () => this.cancelTask && this.cancelTask()
            }
            this.options = {
                ...this.options,
                ...options,
                keepTime: ((time) => {
    
                    if(!time) return this.options.keepTime
                    if(typeof time === "number") return time
    
                    switch(time) {
                        case "aMinute": return 60*1000
                        case "aHour": return 60*60*1000
                        case "aDay": return 24*60*60*1000
                        case "aWeek": return 7*24*60*60*1000
                        case "aMonth": return 30*24*60*60*1000
                        case "aYear": return 365*24*60*60*1000
                    }
    
                })(options.keepTime),
                callbacks: {
                    ...this.options.callbacks,
                    ...options.callbacks
                }
            }
            if(this._status==="listening") {
                this.invalidate()
                this.start()
            }
        }
        return this
    }

    public withConstraints(constraints: {
        network  ?: "connected" | "metered" | "notRoaming" | "unmetered" | "notRequired",
        battery  ?: "charging" | "notLow" | "notRequired",
        storage  ?: boolean,
        idle     ?: boolean,
    }) {
        if(this._status==="intializing") this.buffer.push(() => this.withConstraints(constraints))
        else {
            this.constraints = {
                ...this.constraints,
                ...constraints,
            }
            if(this._status=== "listening") {
                this.invalidate()
                this.start()
            }
        }
        return this
    }

    public withWorkflow<_P extends P,_V extends V=V>(workflow: WorkFlow<_P,_V>) {
        if(this._status==="intializing") this.buffer.push(() => this.withWorkflow(workflow))
        else this._workflow = workflow as unknown as WorkFlow<P,V>
        return this as unknown as Worker<_P,_V>
    }

    public enqueue<_P extends P>(payload: _P, config ?: { priority ?: number, attempts ?: number }) {
        if(this._status==="intializing") this.buffer.push(() => this.enqueue(payload, config))
        else this.store.pushJob({
            id: cuid(),
            payload,
            priority: config && config.priority || 0,
            attempts: config && config.attempts || 1,
            enqueuedDate: new Date().getTime(),
        })
        if(this.options.autoStart) NativeModules.BackgroundWorker.startService(this.id, {
            title: this.options.notification.title,
            message: this.options.notification.message,
            actions: Object.keys(this.options.notification.actions)
        })
        return this as unknown as Worker<_P,V>
    }

    public start() {
        console.log({ options: this.options })
        if(this._status==="intializing") this.buffer.push(() => this.start())
        else if(this._status === "idle") NativeModules.BackgroundWorker.worker(
            this.constraints, 
            {
                title: this.options.notification.title,
                message: this.options.notification.message,
                actions: Object.keys(this.options.notification.actions)
            },
            (id: string) => {
            console.log(`listening at id ${ id }`)
            this._status = "listening"
            const _eventType = "react-native-background-worker-action"+id
            this.emitter.addListener(_eventType, this.actionsListener)
            this.eventType = _eventType
            AppRegistry.registerHeadlessTask(id, () => this.workflow)
            this.id = id
        })
    }

    private actionsListener(action: string) {
        this.options.notification.actions[action]()
    }

    private invalidate() {
        if(this._status==="intializing") this.buffer.push(() => this.invalidate())
        else if(this._status === "listening") {
            NativeModules.BackgroundWorker.cancelWorker(this.id)
            if(this.eventType) this.emitter.removeListener(this.eventType, this.actionsListener)
            this.id = undefined
            this._status = "idle"
        }
    }
}