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

package com.ouyangzn.github.utils;

import rx.functions.Action1;

/**
 * Created by ouyangzn on 2016/9/30.<br/>
 * Description：
 */
public class RxJavaUtil {

  private static final String TAG = RxJavaUtil.class.getSimpleName();

  public static <T> Action1<T> discardResult() {
    return t -> Log.w(TAG,
        "----------discardResult, result = " + (t == null ? "null" : t.toString()));
  }

  public static Action1<Throwable> discardError() {
    return e -> Log.w(TAG, "----------discardError: ", e);
  }
}
