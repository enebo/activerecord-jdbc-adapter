source "https://rubygems.org"

# Rails Dependencies Configuration
if ENV['RAILS']  # Use local clone of Rails
  rails_dir = ENV['RAILS']    
  activerecord_dir = ::File.join(rails_dir, 'activerecord')
  
  if !::File.exist?(rails_dir) && !::File.exist?(activerecord_dir)
    raise "ENV['RAILS'] set but does not point at a valid rails clone"
  end

  activemodel_dir =  ::File.join(rails_dir, 'activemodel')
  activesupport_dir =  ::File.join(rails_dir, 'activesupport')
  actionpack_dir =  ::File.join(rails_dir, 'actionpack')
  actionview_dir =  ::File.join(rails_dir, 'actionview')

  gem 'activerecord', require: false, path: activerecord_dir
  gem 'activemodel', require: false, path: activemodel_dir
  gem 'activesupport', require: false, path: activesupport_dir
  gem 'actionpack', require: false, path: actionpack_dir
  gem 'actionview', require: false, path: actionview_dir

elsif ENV['AR_VERSION'] # Use specific version of AR and not .gemspec version
  version = ENV['AR_VERSION']
  
  if !version.eql?('false') # Don't bundle any versions of AR; use LOAD_PATH
    # Specified as raw number. Use normal gem require.
    if version =~ /^([0-9.])+(_)?(rc|RC|beta|BETA|PR|pre)*([0-9.])*$/
      gem 'activerecord', version, require: nil
    else # Asking for git clone specific version
      if version =~ /^[0-9abcdef]+$/ ||                                 # SHA
         version =~ /^v([0-9.])+(_)?(rc|RC|beta|BETA|PR|pre)*([0-9.])*$/# tag
        opts = {ref: version}
      else                                                              # branch
        opts = {branch: version}
      end

      git 'https://github.com/rails/rails.git', **opts do
        gem 'activerecord', require: false
        gem 'activemodel', require: false
        gem 'activesupport', require: false
        gem 'actionpack', require: false
        gem 'actionview', require: false
      end
    end
  end
else
  if defined? JRUBY_VERSION
    gemspec name: 'activerecord-jdbc-adapter' # Use version from .gemspec
  else # read add_dependency 'activerecord', '~> 8.0' and use the same requirement on MRI
    ar_req = File.read('activerecord-jdbc-adapter.gemspec').match(/add_dependency.*?activerecord.*['"](.*?)['"]/)[1]
    raise "add_dependency 'activerecord', ... line not detected in gemspec" unless ar_req
    gem 'activerecord', ar_req
  end
end

# Core Dependencies
gem 'rake', require: nil

# Development Dependencies
group :development do
  gem 'pry-nav'
  
  group :doc do
    gem 'yard', require: nil
    gem 'kramdown', require: nil
  end
end

# Test Dependencies
group :test do
  # Core testing gems
  gem 'test-unit', require: nil
  gem 'test-unit-context', require: nil
  gem 'mocha', '~> 2.0', require: false
  gem 'bcrypt', '~> 3.1', require: false
  
  # Database adapters for MRI
  platform :mri do
    gem 'mysql2', '~> 0.5', require: nil
    gem 'pg', '~> 1.5', require: nil
    gem 'sqlite3', '~> 2.0', require: nil
  end
  
  # JDBC SQLite version override
  platform :jruby do
    if sqlite_version = ENV['JDBC_SQLITE_VERSION']
      gem 'jdbc-sqlite3', sqlite_version, require: nil
    end
  end
end

# Rails-specific test dependencies
group :rails do
  # Rails testing and support gems
  gem 'builder', require: nil
  gem 'erubis', require: nil
  gem 'msgpack', '~> 1.7', require: false
  gem 'rack', require: nil
  gem 'zeitwerk'

  group :test do
    gem 'minitest-excludes', require: nil
    gem 'minitest-rg', require: nil
    gem 'benchmark-ips', require: nil
  end
end
