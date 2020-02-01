import {AppRegistry} from 'react-native';
import App from './App';
import {name as appName} from './app.json';

// keep react from telling us that our task is been registered multiple times
console.disableYellowBox = true;

AppRegistry.registerComponent(appName, () => App);
