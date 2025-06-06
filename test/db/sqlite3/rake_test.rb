require 'rake_test_support'
require 'db/sqlite3'

# FIXME: This seems to have worked in past versions of Rails but it seems like
# it is nowhere I can find in Rails 5+.  Why was this working before this?
RAILS_ROOT = "."

class SQLite3RakeTest < Test::Unit::TestCase
  include RakeTestSupport

  def db_name; 'rake_test.sqlite3'; end

  def do_setup
    File.delete('rake_test.sqlite3') if File.exist?('rake_test.sqlite3')
  end

  def do_teardown
    File.delete('rake_test.sqlite3') if File.exist?('rake_test.sqlite3')
  end

  test 'rake db:create (and db:drop)' do
    Rake::Task["db:create"].invoke
    assert_true File.exist?('rake_test.sqlite3')

    Rake::Task["db:drop"].invoke
    assert_false File.exist?('rake_test.sqlite3')
  end

  test 'rake db:test:purge' do
    # Rake::Task["db:create"].invoke
    ActiveRecord::Base.establish_connection db_config.merge :database => db_name
    ActiveRecord::Base.connection.create_table('users') { |t| t.string :name }
    ActiveRecord::Base.connection.disconnect!

    Rake::Task["db:test:purge"].invoke

    ActiveRecord::Base.establish_connection db_config.merge :database => db_name
    assert_false ActiveRecord::Base.connection.data_source_exists?('users')
    ActiveRecord::Base.connection.disconnect!
  end

  test 'rake db:structure:dump (and db:structure:load)' do
  # FIXME: sqlite3 at some point changed how their file permissions are set
  #  as well as having two files per db now.  File.delete is not the right
  #  mechanism: https://stackoverflow.com/questions/4406067/how-to-delete-sqlite-database-from-android-programmatically/4420083#4420083
  #  I am not totally sure I understand why we are testing this?  Is this just
  #  to make sure we can do something later in our unit tests?
    omit('FIXME: reevaluate need for test OR change for newer sqlite3 behavior')
    omit('sqlite3 not available') unless self.class.which('sqlite3')
    create_rake_test_database do |connection|
      create_schema_migrations_table(connection)
      connection.create_table('users') { |t| t.string :name; t.timestamps :null => false }
    end

    structure_sql = File.join('db', structure_sql_filename)
    begin
      Dir.mkdir 'db' # db/structure.sql
      Rake::Task["db:structure:dump"].invoke

      assert File.exist?(structure_sql)
      assert_match(/CREATE TABLE .*?users/, File.read(structure_sql))

      # db:structure:load
      File.delete('rake_test.sqlite3')
      Rake::Task["db:structure:load"].invoke

      ActiveRecord::Base.establish_connection db_config.merge :database => db_name
      assert ActiveRecord::Base.connection.data_source_exists?('users')
      ActiveRecord::Base.connection.disconnect!
    ensure
      File.delete(structure_sql) if File.exist?(structure_sql)
      Dir.rmdir 'db'
    end
  end

  test 'rake db:charset' do
    expect_rake_output('UTF-8')
    Rake::Task["db:charset"].invoke
  end

end
