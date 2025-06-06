# ActiveRecord JDBC Adapter

[![Gem Version](https://badge.fury.io/rb/activerecord-jdbc-adapter.svg)][7]

ActiveRecord-JDBC-Adapter (AR-JDBC) is the main database adapter for Rails'
*ActiveRecord* component that can be used with [JRuby][0].
ActiveRecord-JDBC-Adapter provides full or nearly full support for:
**MySQL**, **PostgreSQL**, **SQLite3** and **MSSQL*** (SQLServer).

Unless we get more contributions we will not be supporting more adapters.
Note that the amount of work needed to get another adapter is not huge but
the amount of testing required to make sure that adapter continues to work
is not something we can do with the resources we currently have.

- for **Oracle** database users you are encouraged to use
  https://github.com/rsim/oracle-enhanced
- **MSSQL** adapter's gem parts reside in a [separate repository][8]

Versions are targeted at certain versions of Rails and live on their own branches.

| Gem Version | Rails Version | Branch    | min JRuby | min Java |
| ----------- | ------------- | --------- | --------- | -------- |
| 50.x        | 5.0.x         | 50-stable | 9.1.x     | 7        |
| 51.x        | 5.1.x         | 51-stable | 9.1.x     | 7        |
| 52.x        | 5.2.x         | 52-stable | 9.1.x     | 7        |
| 60.x        | 6.0.x         | 60-stable | 9.2.7     | 8        |
| 61.x        | 6.1.x         | 61-stable | 9.2.7     | 8        |
| 70.x        | 7.0.x         | 70-stable | 9.3.0     | 8        |
| 71.x        | 7.1.x         | 71-stable | 9.4.3     | 8        |
| 72.x        | 7.2.x         | 72-stable | 9.4.3     | 8        |
| 80.x        | 8.0.x         | master    | 10.0.0    | 8        |

Note: 80.x is still under development and not supported yet.

Note that JRuby 9.1.x and JRuby 9.2.x are at end-of-life. We recommend Java 8
at a minimum for all versions.

## Using ActiveRecord JDBC

### Inside Rails

To use AR-JDBC with JRuby on Rails:

1. Choose the adapter you wish to gem install. The following pre-packaged
adapters are available:

  - MySQL (`activerecord-jdbcmysql-adapter`)
  - PostgreSQL (`activerecord-jdbcpostgresql-adapter`)
  - SQLite3 (`activerecord-jdbcsqlite3-adapter`)
  - MSSQL (`activerecord-jdbcsqlserver-adapter`)

2. If you're generating a new Rails application, use the following command:

    jruby -S rails new sweetapp

3. Configure your *database.yml* in the normal Rails style:

```yml
development:
  adapter: mysql2
  database: blog_development
  username: blog
  password: 1234
```

For JNDI data sources, you may simply specify the JNDI location as follows, it's
recommended to use the same adapter: setting as one would configure when using
"bare" (JDBC) connections e.g. :

```yml
production:
  adapter: postgresql
  jndi: jdbc/PostgreDS
```

**NOTE:** any other settings such as *database:*, *username:*, *properties:* make
no difference since everything is already configured on the JNDI DataSource end.

JDBC driver specific properties might be set if you use an URL to specify the DB
or preferably using the *properties:* syntax:

```yml
production:
  adapter: mysql2
  username: blog
  password: blog
  url: "jdbc:mysql://localhost:3306/blog?profileSQL=true"
  properties: # specific to com.mysql.jdbc.Driver
    socketTimeout:  60000
    connectTimeout: 60000
```

#### MySQL specific notes

Depending on the MySQL server configuration, it might be required to set
additional connection properties for date/time support to work correctly. If you
encounter problems, try adding this to your database configuration:

```yml
  properties:
    serverTimezone: <%= java.util.TimeZone.getDefault.getID %>
```

The correct timezone depends on the system setup, but the one shown is a good
place to start and is actually the correct setting for many systems.


### Standalone with ActiveRecord

Once the setup is made (see below) you can establish a JDBC connection like this
(e.g. for `activerecord-jdbcderby-adapter`):

```ruby
ActiveRecord::Base.establish_connection(
  adapter: 'sqlite3',
  database: 'db/my-database'
)
```

#### Using Bundler

Proceed as with Rails; specify `ActiveRecord` in your Bundle along with the
chosen JDBC adapter(s), this time sample *Gemfile* for MySQL:

```ruby
gem 'activerecord', '~> 6.0.3'
gem 'activerecord-jdbcmysql-adapter', '~> 60.2', :platform => :jruby
```

When you `require 'bundler/setup'` everything will be set up for you as expected.

#### Without Bundler

Install the needed gems with JRuby, for example:

    gem install activerecord -v "~> 6.0.3"
    gem install activerecord-jdbc-adapter -v "~> 60.2" --ignore-dependencies

If you wish to use the adapter for a specific database, you can install it
directly and the (jdbc-) driver gem (dependency) will be installed as well:

    jruby -S gem install activerecord-jdbcmysql-adapter -v "~> 60.2"

Your program should include:

```ruby
require 'active_record'
require 'activerecord-jdbc-adapter' if defined? JRUBY_VERSION
```

## Source

The source for activerecord-jdbc-adapter is available using git:

    git clone git://github.com/jruby/activerecord-jdbc-adapter.git

Please note that the project manages multiple gems from a single repository,
if you're using *Bundler* >= 1.2 it should be able to locate all gemspecs from
the git repository. Sample *Gemfile* for running with (MySQL) master:

```ruby
gem 'activerecord-jdbc-adapter', :github => 'jruby/activerecord-jdbc-adapter'
gem 'activerecord-jdbcmysql-adapter', :github => 'jruby/activerecord-jdbc-adapter'
```

## Getting Involved

Please read our [CONTRIBUTING](CONTRIBUTING.md) & [RUNNING_TESTS](RUNNING_TESTS.md)
guides for starters. You can always help us by maintaining AR-JDBC's [wiki][5].

## Feedback

Please report bugs at our [issue tracker][3]. If you're not sure if
something's a bug, feel free to pre-report it on the [mailing lists][1] or
ask on the #JRuby IRC channel on http://freenode.net/ (try [web-chat][6]).

## Authors

This project was originally written by [Nick Sieger](http://github.com/nicksieger)
and [Ola Bini](http://github.com/olabini) with lots of help from the JRuby community.
Polished 3.x compatibility and 4.x support (for AR-JDBC >= 1.3.0) was managed by
[Karol Bucek](http://github.com/kares) among others. Support for Rails 6.0 and 6.1 was
contributed by [shellyBits GmbH](https://shellybits.ch/)

## License

ActiveRecord-JDBC-Adapter is open-source released under the BSD/MIT license.
See [LICENSE.txt](LICENSE.txt) included with the distribution for details.

Open-source driver gems within AR-JDBC's sources are licensed under the same
license the database's drivers are licensed. See each driver gem's LICENSE.txt.

[0]: http://www.jruby.org/
[1]: http://jruby.org/community
[2]: http://github.com/jruby/activerecord-jdbc-adapter/blob/master/activerecord-jdbcmssql-adapter
[3]: https://github.com/jruby/activerecord-jdbc-adapter/issues
[4]: http://github.com/nicksieger/activerecord-cachedb-adapter
[5]: https://github.com/jruby/activerecord-jdbc-adapter/wiki
[6]: https://webchat.freenode.net/?channels=#jruby
[7]: http://badge.fury.io/rb/activerecord-jdbc-adapter
[8]: https://github.com/jruby/activerecord-jdbcsqlserver-adapter
