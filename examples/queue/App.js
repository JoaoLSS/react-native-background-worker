/* eslint-disable prettier/prettier */
/* eslint-disable react-native/no-inline-styles */
import React, {useEffect, useState, useReducer, useCallback} from 'react';
import {SafeAreaView, StatusBar, FlatList, Text, ToastAndroid, AppState, Button, View} from 'react-native';
import WorkManager from 'react-native-background-worker';

const reducer = (state, { id, info }) => ({ ...state, [id]: info });

const App = () => {

  const [ids, setId] = useState([]);
  const [subscriptions, setSubscription] = useState([]);
  const [infos, dispatch] = useReducer(reducer, {});

  useEffect(() => {
    WorkManager.setWorker({
      type: 'queue',                                    // [REQUIRED] worker's type, could be 'periodic' or 'queue'.
      name: 'photo_uploader',                           // [REQUIRED] worker's name, remember to create a drawable with the
                                                        // same name to be displayed with the notification.
      notification: {
        title: 'uploading photo',                       // [REQUIRED] notification title.
        text: 'photos are been uploaded to the cloud',  // [REQUIRED] notification text.
      },
      workflow: async (name) => {                       // [REQUIRED] the workflow this worker will perform.

        ToastAndroid.show('uploading ' + name,ToastAndroid.SHORT);
        // !! DONT USE setTimeout INSIDE WORKFLOW BECAUSE IT IS NOT RELIABLE ON HEADLESS TASKS !!
        // !! THIS IS FOR EXAMPLE PURPOSE ONLY !!
        const result = await new Promise((resolve) => {
          setTimeout(() => {
            resolve(Math.random());
          },10000);
        });
        // in case of failure or success, the value will be stored by the workmanger
        // in case of retry, no value will be saved, and the payload will imediately be reescheduled
        if ( result < 0.33 ) return { result: 'failure', value: name + ' could not be uploaded' };
        else if ( result < 0.66 ) return { result: 'retry' };
        else return { result: 'success', value: name + ' was uploaded' };

      },
      timeout: 1,                                         // [OPTIONAL] the headless task timeout in minutes, defaults to 10.
      foregroundBehaviour: 'headlessTask',                // [OPTIONAL] the worker's behaviour when app is in foreground,
                                                          // could be 'blocking','foreground' or 'headlessTask', since react is
                                                          // very sensible to tasks that demand processing power, this default
                                                          // to blocking.
      constraints: {
          network: 'connected',                           // [OPTIONAL] network constraint for this worker.
          battery: 'notRequired',                         // [OPTIONAL] battery constraint for this worker.
          storage: 'notRequired',                         // [OPTIONAL] storage constraint for this worker.
          idle: 'notRequired',                            // [OPTIONAL] usage constraint for this worker.
      },
    });
  }, []);

  const enqueue = useCallback(() => {
    // this callback will enqueue the photo and set the subscription to watch its state
    // !! REMEMBER TO PERSISTENTLY SAVE THE RETURNED IDS SO YOU CAN CONSULT THE STATE AFTER !!
    WorkManager.enqueue({ worker: 'photo_uploader', payload: 'photo ' + ids.length })
      .then(id => {
        setId(oldIds => [id, ...oldIds]);
        const subs = WorkManager.addListener(id,(info) => dispatch({ id, info }));
        setSubscription(oldSubs => [subs, ...oldSubs]);
      });
  });

  // as the subscriptions are making use of the dispatch function, remeber to clean them, otherwise the app will crash
  useEffect(() => () => subscriptions.map(subs => subs()),[]);

  return (
    <>
      <StatusBar barStyle="dark-content" />
      <SafeAreaView style={{ flex: 1 }}>
        <FlatList
          style={{ flex: 1 }}
          data={ids}
          renderItem={({item}) => (
            <View style={{ borderColor: 'black', borderWidth: 1, padding: 10 }}>
              <Text>{item}</Text>
              {
                infos[item] ?
                <View>
                  <Text>{'state: ' + infos[item].state}</Text>
                  <Text>{'attempts: ' + infos[item].attemptCount}</Text>
                  <Text>{'result: ' + infos[item].value}</Text>
                </View> : null
              }
            </View>
          )}
        />
        <View style={{ padding: 20 }}>
          <Button onPress={enqueue} title="send photo"/>
        </View>
      </SafeAreaView>
    </>
  );
};

export default App;
