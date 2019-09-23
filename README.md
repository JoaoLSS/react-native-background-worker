# react-native-background-worker

## Getting started

`$ npm install react-native-background-worker --save`

### Mostly automatic installation

`$ react-native link react-native-background-worker`

### Manual installation


#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-background-worker` and add `BackgroundWorker.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libBackgroundWorker.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

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
      compile project(':react-native-background-worker')
  	```


## Usage
```javascript
import BackgroundWorker from 'react-native-background-worker';

// TODO: What to do with the module?
BackgroundWorker;
```
