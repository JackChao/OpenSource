/*
 * Copyright (c) 2016.  ouyangzn   <ouyangzn@163.com>
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

package com.ouyangzn.github.bean.localbean;

import com.ouyangzn.github.utils.Formatter;
import java.util.Date;

/**
 * Created by ouyangzn on 2016/9/14.<br/>
 * Description：搜索因子（即各种搜索条件）
 */
public class SearchFactor {
  /** 关键字 */
  public String keyword;
  /** 项目的语言 */
  public String language;
  /** 项目创建时间 */
  private Date createDate;

  public String getCreateDate() {
    return createDate == null ? null
        : Formatter.formatDate(createDate, Formatter.FORMAT_YYYY_MM_DD);
  }

  public void setCreateDate(Date createDate) {
    this.createDate = createDate;
  }

  @Override public String toString() {
    return "SearchFactor{" +
        "keyword='" + keyword + '\'' +
        ", createDate='" + getCreateDate() + '\'' +
        ", language='" + language + '\'' +
        '}';
  }
}
