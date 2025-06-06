require 'db/sqlite3'
require 'models/data_types'
require 'models/validates_uniqueness_of_string'
require 'simple'
require 'jdbc_common'

class SQLite3SimpleTest < Test::Unit::TestCase
  include SimpleTestMethods
  include ActiveRecord3TestMethods
  include ColumnNameQuotingTests
  include XmlColumnTestMethods
  include ExplainSupportTestMethods
  include CustomSelectTestMethods

  def test_execute_insert
    user = User.create! :login => 'user1'
    Entry.create! :title => 'E1', :user_id => user.id

    assert_equal 1, Entry.count
    # NOTE: AR actually returns an empty [] (not an ID) !?
    connection.exec_insert "INSERT INTO entries (title, content) VALUES ('Execute Insert', 'This now works with SQLite3')", nil, []
    assert_equal 2, Entry.count
  end

  def test_execute_update
    user = User.create! :login => 'user1'
    Entry.create! :title => 'E1', :user_id => user.id

    affected_rows = connection.exec_update "UPDATE entries SET title = 'Execute Update' WHERE id = #{Entry.first.id}"
    assert_equal 1, affected_rows if defined? JRUBY_VERSION # sqlite3 returns []
    assert_equal 'Execute Update', Entry.first.title
  end

  def test_columns
    cols = ActiveRecord::Base.connection.columns("entries")
    assert cols.find { |col| col.name == "title" }
  end

  def test_remove_column
    assert_nothing_raised do
      ActiveRecord::Schema.define do
        add_column "entries", "test_remove_column", :string
      end
    end

    cols = ActiveRecord::Base.connection.columns("entries")
    assert cols.find {|col| col.name == "test_remove_column"}

    #assert_nothing_raised do
      ActiveRecord::Schema.define do
        remove_column "entries", "test_remove_column"
      end
    #end

    cols = ActiveRecord::Base.connection.columns("entries")
    assert_nil cols.find {|col| col.name == "test_remove_column"}
  end

  def test_rename_column
    #assert_nothing_raised do
      ActiveRecord::Schema.define do
        rename_column "entries", "title", "name"
      end
    #end

    cols = ActiveRecord::Base.connection.columns("entries")
    assert_not_nil cols.find {|col| col.name == "name"}
    assert_nil cols.find {|col| col.name == "title"}

    assert_nothing_raised do
      ActiveRecord::Schema.define do
        rename_column "entries", "name", "title"
      end
    end

    cols = ActiveRecord::Base.connection.columns("entries")
    assert_not_nil cols.find {|col| col.name == "title"}
    assert_nil cols.find {|col| col.name == "name"}
  end

  def test_rename_column_preserves_content
    title = "First post!"
    content = "Hello from JRuby on Rails!"
    rating = 205.76
    user = User.create! :login => "something"
    entry = Entry.create! :title => title, :content => content, :rating => rating, :user => user

    entry.reload
    #assert_equal title, entry.title
    #assert_equal content, entry.content
    #assert_equal rating, entry.rating

    ActiveRecord::Schema.define do
      rename_column "entries", "title", "name"
      rename_column "entries", "rating", "popularity"
    end

    entry = Entry.find(entry.id)
    assert_equal title, entry.name
    assert_equal content, entry.content
    assert_equal rating, entry.popularity
  end

  def test_rename_column_preserves_index
    assert_equal(0, connection.indexes(:entries).size)

    index_name = "entries_index"

    ActiveRecord::Schema.define do
      add_index "entries", "title", :name => index_name
    end

    indexes = connection.indexes(:entries)
    assert_equal(1, indexes.size)
    assert_equal "entries", indexes.first.table.to_s
    assert_equal index_name, indexes.first.name
    assert ! indexes.first.unique
    assert_equal ["title"], indexes.first.columns

    ActiveRecord::Schema.define do
      rename_column "entries", "title", "name"
    end

    indexes = connection.indexes(:entries)
    assert_equal(1, indexes.size)
    assert_equal "entries", indexes.first.table.to_s
    assert_equal index_name, indexes.first.name
    assert ! indexes.first.unique
    assert_equal ["name"], indexes.first.columns
  end

  def test_column_default
    assert_nothing_raised do
      ActiveRecord::Schema.define do
        add_column "entries", "test_column_default", :string
      end
    end

    columns = ActiveRecord::Base.connection.columns("entries")
    assert column = columns.find{ |c| c.name == "test_column_default" }
    assert_equal column.default, nil
  end

  def test_change_column_default
    assert_nothing_raised do
      ActiveRecord::Schema.define do
        add_column "entries", "test_change_column_default", :string, :default => "unchanged"
      end
    end

    columns = ActiveRecord::Base.connection.columns("entries")
    assert column = columns.find{ |c| c.name == "test_change_column_default" }
    assert_equal column.default, 'unchanged'

    assert_nothing_raised do
      ActiveRecord::Schema.define do
        change_column_default "entries", "test_change_column_default", "changed"
      end
    end

    columns = ActiveRecord::Base.connection.columns("entries")
    assert column = columns.find{ |c| c.name == "test_change_column_default" }
    assert_equal column.default, 'changed'
  end

  def test_change_column
    assert_nothing_raised do
      ActiveRecord::Schema.define do
        add_column "entries", "test_change_column", :string
      end
    end

    columns = ActiveRecord::Base.connection.columns("entries")
    assert column = columns.find{ |c| c.name == "test_change_column" }
    assert_equal column.type, :string

    assert_nothing_raised do
      ActiveRecord::Schema.define do
        change_column "entries", "test_change_column", :integer
      end
    end

    columns = ActiveRecord::Base.connection.columns("entries")
    assert column = columns.find{ |c| c.name == "test_change_column" }
    assert_equal column.type, :integer
  end

  def test_change_column_with_new_precision_and_scale
    Entry.delete_all
    Entry.
      connection.
      change_column "entries", "rating", :decimal, :precision => 9, :scale => 7
    Entry.reset_column_information
    change_column = Entry.columns_hash["rating"]
    assert_equal 9, change_column.precision
    assert_equal 7, change_column.scale
  end

  def test_change_column_preserve_other_column_precision_and_scale
    Entry.delete_all
    Entry.
      connection.
      change_column "entries", "rating", :decimal, :precision => 9, :scale => 7
    Entry.reset_column_information

    rating_column = Entry.columns_hash["rating"]
    assert_equal 9, rating_column.precision
    assert_equal 7, rating_column.scale

    Entry.
      connection.
      change_column "entries", "title", :string, :null => false
    Entry.reset_column_information

    rating_column = Entry.columns_hash["rating"]
    assert_equal 9, rating_column.precision
    assert_equal 7, rating_column.scale
  end

  # @override
  def test_big_decimal
    test_value = 1234567890.0 # FINE just like native adapter
    db_type = DbType.create!(:big_decimal => test_value)
    db_type = DbType.find(db_type.id)
    assert_equal test_value, db_type.big_decimal

    test_value = 1234567890_123456 # FINE just like native adapter
    db_type = DbType.create!(:big_decimal => test_value)
    db_type = DbType.find(db_type.id)
    assert_equal test_value, db_type.big_decimal

    test_value = BigDecimal('1234567890_1234567890.0')
    db_type = DbType.create!(:big_decimal => test_value)
    db_type = DbType.find(db_type.id)
    #assert_equal 12345678901234567168, db_type.big_decimal # SQLite3/MRI way
    #assert_equal 12345678901234600000, db_type.big_decimal # the JDBC way

    # NOTE: this is getting f*cked up in the native adapter as well although
    # differently and only when inserted manually - works with PSs (3.1+) :
    test_value = 1234567890_1234567890.0 # (Float)
    db_type = DbType.create!(:big_decimal => test_value)
    db_type = DbType.find(db_type.id)
    pend 'TODO: compare and revisit how native adapter behaves'
    # TODO native gets us 12345678901234567000.0 JDBC gets us 1
    # <1.23456789012346e+19> expected but was <12345678901234600000>
    assert_equal test_value, db_type.big_decimal
    #super
  end

  # @override SQLite3 returns FLOAT (JDBC type) for DECIMAL columns
  def test_custom_select_decimal
    model = DbType.create! :sample_small_decimal => ( decimal = BigDecimal('5.45') )
    model = DbType.where("id = #{model.id}").select('sample_small_decimal AS custom_decimal').first
    assert_equal decimal, model.custom_decimal
    #assert_instance_of BigDecimal, model.custom_decimal
  end

  # @override SQLite3 returns String for columns created with DATETIME type
  def test_custom_select_datetime
    my_time = Time.utc 2013, 03, 15, 19, 53, 51, 0 # usec
    model = DbType.create! :sample_datetime => my_time
    model = DbType.where("id = #{model.id}").select('sample_datetime AS custom_sample_datetime').first
    assert_match my_time.to_fs(:db), model.custom_sample_datetime # '2013-03-15 18:53:51.000000'
  end

  # @override SQLite3 JDBC returns VARCHAR type for column
  def test_custom_select_date
    my_date = Time.local(2000, 01, 30, 0, 0, 0, 0).to_date
    model = DbType.create! :sample_date => my_date
    model = DbType.where("id = #{model.id}").select('sample_date AS custom_sample_date').first
    assert_equal my_date.to_fs(:db), model.custom_sample_date
  end

  # @override
  def test_custom_select_datetime__non_raw_date_time
    skip 'not-relevant on SQLite3'
  end if defined? JRUBY_VERSION

  def test_custom_select_date__non_raw_date_time
    skip 'not-relevant on SQLite3'
  end if defined? JRUBY_VERSION

  # @override
  def test_time_according_to_precision
    skip "SQLite3 time formatting changed in Rails 8.0 - now returns '+0000' instead of 'UTC' for timezone"
  end

  test 'returns correct visitor type' do
    assert_not_nil visitor = connection.instance_variable_get(:@visitor)
    assert defined? Arel::Visitors::SQLite
    assert_kind_of Arel::Visitors::SQLite, visitor
  end

  # Override failing tests due to SQLite3 limitations
  def test_save_timestamp_with_usec
    skip "SQLite3 does not support full microsecond precision - only stores up to milliseconds (3 digits)"
  end

  def test_time_with_default_timezone_utc
    skip "SQLite3 does not preserve timezone information - times are stored as strings without zone"
  end

  def test_time_with_default_timezone_local
    skip "SQLite3 does not preserve timezone information - times are stored as strings without zone"
  end
end
