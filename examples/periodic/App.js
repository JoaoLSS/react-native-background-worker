/* eslint-disable prettier/prettier */
/* eslint-disable react-native/no-inline-styles */
import React, {useEffect, useState} from 'react';
import {SafeAreaView, StatusBar, FlatList, Text, ToastAndroid, AppState} from 'react-native';
import WorkManager from 'react-native-background-worker';

const data = [];

// this is how to set a periodic worker
async function setUpdater(repeatInterval) {
  const updaterId = await WorkManager.setWorker(/*periodic worker:*/{
      type: 'periodic',                                   // [REQUIRED] worker's type, could be 'periodic' or 'queue'.
      name: 'news_updater',                                // [REQUIRED] worker's name, remember to create a drawable with the
                                                          // same name to be displayed with the notification.
      notification: {
          title: 'Updating your news',                    // [REQUIRED] notification title.
          text: 'Don`t worry, we will keep you fresh 😎', // [REQUIRED] notification text.
      },
      workflow: async () => {                             // [REQUIRED] the workflow this worker will perform.

          ToastAndroid.show('running updater',ToastAndroid.SHORT);
          await new Promise((resolve) => {
            setTimeout(() => {
              data.push(new Date().toLocaleString());
              resolve();
            },10000);
          });

      },
      timeout: 1,                                         // [OPTIONAL] the headless task timeout in minutes, defaults to 10.
      foregroundBehaviour: 'foreground',                  // [OPTIONAL] the worker's behaviour when app is in foreground,
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
  return updaterId;
}

const App = () => {
  const [updaterState, setUpdaterState] = useState('unknown');

  useEffect(() => {
    let subscription = null;
    AppState.addEventListener('change',(appState) => {
      if (appState === 'active') {
        ToastAndroid.show('15 min',ToastAndroid.SHORT);
        setUpdater(15)
        .then((id) => {
          subscription = WorkManager.addListener(id, ({ state }) => setUpdaterState(state));
        });
      }
      else {
        subscription && subscription();
        ToastAndroid.show('30 min',ToastAndroid.SHORT);
        setUpdater(30);
      }
    });
    return () => {
      subscription && subscription();
    };
  }, []);

  return (
    <>
      <StatusBar barStyle="dark-content" />
      <SafeAreaView>
        <Text style={{padding: 20, fontSize: 20, textAlign: 'center'}}>
          {updaterState}
        </Text>
        <FlatList
          data={data}
          refreshing={updaterState === 'running'}
          renderItem={({item}) => (
            <Text
              style={{
                padding: 20,
                fontSize: 15,
                borderColor: 'black',
                borderWidth: 1,
              }}>
              {item}
            </Text>
          )}
        />
      </SafeAreaView>
    </>
  );
};

export default App;
