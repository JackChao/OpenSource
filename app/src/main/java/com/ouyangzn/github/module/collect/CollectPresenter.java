/*
 * Copyright (c) 2016.  ouyangzn   <email : ouyangzn@163.com>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ouyangzn.github.module.collect;

import android.content.Context;
import com.ouyangzn.github.App;
import com.ouyangzn.github.bean.localbean.CollectedRepo;
import com.ouyangzn.github.db.DBConstans.CollectedRepoFields;
import com.ouyangzn.github.module.collect.CollectContract.ICollectPresenter;
import com.ouyangzn.github.utils.Log;
import com.trello.rxlifecycle.LifecycleProvider;
import com.trello.rxlifecycle.android.ActivityEvent;
import io.realm.Realm;
import io.realm.RealmModel;
import io.realm.RealmResults;
import io.realm.Sort;
import java.util.ArrayList;
import java.util.List;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.MainThreadSubscription;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by ouyangzn on 2016/9/27.<br/>
 * Description：
 */
public class CollectPresenter extends ICollectPresenter {

  private final String TAG = CollectPresenter.class.getSimpleName();

  private LifecycleProvider<ActivityEvent> mProvider;
  private App mApp;
  private Realm mRealm;
  private RealmResults<CollectedRepo> mCollectList;

  private Subscription mQueryByKeySub;

  public CollectPresenter(Context context, LifecycleProvider<ActivityEvent> provider) {
    mProvider = provider;
    mApp = (App) context.getApplicationContext();
    mRealm = mApp.getGlobalRealm();
  }

  public static <T extends RealmModel> List<T> subList(RealmResults<T> list, int start, int end) {
    if (start >= list.size()) start = list.size() - 1;
    if (start < 0) start = 0;
    if (end > list.size()) end = list.size();
    if (start > end) {
      int temp = start;
      start = end;
      end = temp;
    }
    // 转换成普通的List，realm的RealmResults很多API不能用
    return new ArrayList<>(list.subList(start, end));
  }

  @Override public void queryByKey(String key) {
    if (mQueryByKeySub != null) mQueryByKeySub.unsubscribe();
    // 收藏的项目一般不会太多，暂时不做分页处理
    mQueryByKeySub = mRealm.where(CollectedRepo.class)
        .contains(CollectedRepoFields.FIELD_DESCRIPTION, key)
        .or()
        .contains(CollectedRepoFields.FIELD_FULL_NAME, key)
        .findAllSortedAsync(CollectedRepoFields.FIELD_COLLECT_TIME, Sort.DESCENDING)
        .asObservable()
        .subscribeOn(AndroidSchedulers.mainThread())
        .observeOn(AndroidSchedulers.mainThread())
        // 使用rxlifecycle，自由指定取消订阅的时间点
        .compose(mProvider.bindUntilEvent(ActivityEvent.DESTROY))
        .subscribe(results -> {
          mView.showCollectQueryByKey(new ArrayList<>(results));
        }, error -> {
          Log.e(TAG, "----------查询收藏,queryByKey出错：", error);
          mView.showQueryByKeyFailure();
        });
    //addSubscription(mQueryByKeySub);
  }

  @Override public void queryCollect(int page, int countEachPage) {
    // ----------方式1：因realm在main线程获取，只能在main线程查询----------
    // 优先从缓存查
    //Observable.concat(Observable.just(mCollectList), mRealm.asObservable()
    //    .concatMap(realm -> Observable.just(
    //        realm.where(CollectedRepo.class).findAllSorted(CollectedRepoFields.FIELD_COLLECT_TIME, Sort.DESCENDING))))
    //    .filter(results -> results != null)
    //    // 取第一个有数据的结果
    //    .first()
    //    // realm在main线程创建，必须在main线程使用
    //    .subscribeOn(AndroidSchedulers.mainThread())
    //    .map(results -> {
    //      // 缓存查询到的结果
    //      mCollectList = results;
    //      return subList(results, page * countEachPage, (page + 1) * countEachPage);
    //    })
    //    // 使用rxlifecycle，自由指定取消订阅的时间点
    //    .compose(mProvider.bindUntilEvent(ActivityEvent.DESTROY))
    //    .subscribe(repoList -> {
    //      mView.showCollect(repoList);
    //    }, error -> {
    //      Log.e(TAG, "---------查询收藏项目出错：", error);
    //      mView.showErrorOnQueryFailure();
    //    });
    // ----------方式2：间接通过异步查询----------
    // 缓存优先
    if (mCollectList != null && mCollectList.isLoaded()) {
      mView.showCollect(subList(mCollectList, page * countEachPage, (page + 1) * countEachPage));
      return;
    }
    mRealm.where(CollectedRepo.class)
        .findAllSortedAsync(CollectedRepoFields.FIELD_COLLECT_TIME, Sort.DESCENDING)
        .asObservable()
        // realm在main线程创建,不能指定为io线程
        //.subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        // 使用rxlifecycle，自由指定取消订阅的时间点
        .compose(mProvider.bindUntilEvent(ActivityEvent.DESTROY))
        .subscribe(repoList -> {
          mCollectList = repoList;
          mView.showCollect(subList(repoList, page * countEachPage, (page + 1) * countEachPage));
        }, error -> {
          Log.e(TAG, "---------查询收藏项目出错：", error);
          mView.showErrorOnQueryFailure();
        });
  }

  @Override public void cancelCollectRepo(final CollectedRepo repo) {
    // ---> reason: repo is bound to the UI thread, and repo.id is translated to database access (repo.realmGet$id()) by
    //              Realm's transformer. This method call can be done only from the bound thread.
    // 实际上repo是由realm管理的代理对象CollectedRepoRealmProxy，而不是真正的CollectedRepo，因此repo的访问需要在原来绑定repo的线程
    // repo被绑定在main线程，所以不能在其他线程访问
    final int id = repo.id;
    mApp.getGlobalRealm().executeTransactionAsync(bgRealm -> {
      // 此操作也不行，会抛异常：Realm access from incorrect thread
      //repo.collectTime = 0;
      // 此操作不会抛异常
      //CollectedRepo collectedRepo = new CollectedRepo();
      //collectedRepo.id = 1;
      //bgRealm.copyToRealm(collectedRepo);
      // 此操作却会抛异常：Realm access from incorrect thread
      //bgRealm.where(CollectedRepo.class).equalTo("id", repo.id).findAll().deleteAllFromRealm();
      bgRealm.where(CollectedRepo.class)
          .equalTo(CollectedRepoFields.FIELD_ID, id)
          .findAll()
          .deleteAllFromRealm();
    }, () -> {
      mView.showCollectionCanceled();
    }, error -> {
      Log.e(TAG, "---------取消收藏失败:", error);
      mView.showCollectionCancelFailure();
    });
    // ----------rxJava方式----------
    //Observable.create(new DeleteCollectedObservable(repo.id))
    //    .subscribeOn(AndroidSchedulers.mainThread())
    //    .subscribe(Void -> {
    //      mView.showCollectionCanceled();
    //    }, error -> {
    //      Log.e(TAG, "---------取消收藏失败:", error);
    //      mView.showCollectionCancelFailure();
    //    });
  }

  @Override protected void onDestroy() {
    if (mCollectList != null) {
      mCollectList.removeChangeListeners();
      mCollectList = null;
    }
    if (mRealm != null) {
      mRealm.close();
      mRealm = null;
    }
  }

  public final class DeleteCollectedObservable implements Observable.OnSubscribe<Void> {

    private int mId;

    public DeleteCollectedObservable(int id) {
      this.mId = id;
    }

    @Override public void call(final Subscriber<? super Void> subscriber) {
      MainThreadSubscription.verifyMainThread();
      mRealm.executeTransactionAsync(bgRealm -> {
        bgRealm.where(CollectedRepo.class)
            .equalTo(CollectedRepoFields.FIELD_ID, mId)
            .findAll()
            .deleteAllFromRealm();
      }, () -> {
        if (!subscriber.isUnsubscribed()) {
          subscriber.onNext(null);
          subscriber.onCompleted();
        }
      }, error -> {
        if (!subscriber.isUnsubscribed()) {
          subscriber.onError(error);
        }
      });

      //subscriber.add(new MainThreadSubscription() {
      //  @Override
      //  protected void onUnsubscribe() {
      //   }
      //});
    }
  }
}
